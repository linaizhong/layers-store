/**************************************************************************
 *  Copyright (C) 2010 Atlas of Living Australia
 *  All Rights Reserved.
 *
 *  The contents of this file are subject to the Mozilla Public
 *  License Version 1.1 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of
 *  the License at http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS
 *  IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 *  implied. See the License for the specific language governing
 *  rights and limitations under the License.
 ***************************************************************************/
package au.org.ala.layers.dao;

import au.org.ala.layers.dto.Field;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.simple.ParameterizedBeanPropertyRowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * @author ajay
 */
@Service("fieldDao")
public class FieldDAOImpl implements FieldDAO {

    /**
     * log4j logger
     */
    private static final Logger logger = Logger.getLogger(FieldDAOImpl.class);

    private SimpleJdbcTemplate jdbcTemplate;
    private SimpleJdbcInsert insertField;

    @Resource(name = "layerIntersectDao")
    private LayerIntersectDAO layerIntersectDao;

    @Resource(name = "layerDao")
    private LayerDAO layerDao;

    @Resource(name = "dataSource")
    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new SimpleJdbcTemplate(dataSource);
        this.insertField = new SimpleJdbcInsert(dataSource).withTableName("fields")
                .usingColumns("id", "name", "\"desc\"", "sname", "sdesc", "sid", "addtomap", "\"intersect\"",
                        "defaultlayer", "enabled", "layerbranch", "analysis", "indb", "spid", "namesearch", "type", "last_update");
    }

    @Override
    public List<Field> getFields() {
        return getFields(false);
    }

    @Override
    public List<Field> getFields(boolean enabledFieldsOnly) {
        logger.info("Getting a list of all fields");
        String sql = "select * from fields";
        if (enabledFieldsOnly) {
            sql += " where enabled=true";
        }
        return jdbcTemplate.query(sql, ParameterizedBeanPropertyRowMapper.newInstance(Field.class));
    }

    @Override
    public Field getFieldById(String id) {
        return getFieldById(id, true);
    }

    @Override
    public Field getFieldById(String id, boolean enabledFieldsOnly) {
        logger.info("Getting enabled field info for id = " + id);
        String sql = "select *, number_of_objects from fields, (select count(*) as number_of_objects from objects where fid = ? ) o where id = ? ";
        if (enabledFieldsOnly) {
            sql += " and enabled=true";
        }
        List<Field> l = jdbcTemplate.query(sql, ParameterizedBeanPropertyRowMapper.newInstance(Field.class), id, id);
        if (l.size() > 0) {
            return l.get(0);
        } else {
            return null;
        }
    }

    @Override
    public List<Field> getFieldsByDB() {
        if (layerIntersectDao.getConfig().getLayerIndexUrl() != null) {
            return layerIntersectDao.getConfig().getFieldsByDB();
        } else {
            //return hibernateTemplate.find("from Field where enabled=true and indb=true");
            logger.info("Getting a list of all enabled fields with indb");
            String sql = "select * from fields where enabled=TRUE and indb=TRUE";
            return jdbcTemplate.query(sql, ParameterizedBeanPropertyRowMapper.newInstance(Field.class));
        }

    }

    @Override
    public synchronized void addField(Field field) {
        logger.info("Add new field for " + field.getName());

        Map<String, Object> parameters = field.toMap();
        parameters.remove("id");

        //calc new fieldId
        String idPrefix = "Contextual".equalsIgnoreCase(layerDao.getLayerById(Integer.parseInt(field.getSpid())).getType())
                ? "cl" : "el";

        //test for default id
        String newId = getFieldById(idPrefix + field.getSpid()) == null ? idPrefix + field.getSpid() : null;
        if (newId == null) {
            //calculate next field Id using general form: prefix (n x 1000 + layerId)
            String idEnd = field.getSpid();
            while (idEnd.length() < 3) {
                idEnd = "0" + idEnd;
            }
            int maxNFound = 0;
            for (Field f : getFields(false)) {
                if (f.getId().startsWith(idPrefix) && f.getId().endsWith(idEnd)) {
                    if (f.getId().length() - idEnd.length() > 2) {
                        int n = Integer.parseInt(f.getId().substring(2, f.getId().length() - idEnd.length()));
                        if (n > maxNFound) {
                            maxNFound = n;
                        }
                    }
                }
            }

            newId = idPrefix + (maxNFound + 1) + idEnd;
        }
        parameters.put("id", newId);
        //fix for field 'desc' and 'intersect'
        if (parameters.containsKey("desc")) {
            parameters.put("\"desc\"", parameters.get("desc"));
            parameters.remove("desc");
        }
        if (parameters.containsKey("intersect")) {
            parameters.put("\"intersect\"", parameters.get("intersect"));
            parameters.remove("intersect");
        }

        insertField.execute(parameters);

        field.setId(newId);
    }

    @Override
    public void updateField(Field field) {
        logger.info("Updating field metadata for " + field.getName());

        String sql = "update fields set name=:name, " +
                "\"desc\"=:desc, type=:type, " +
                "spid=:spid, sid=:sid, sname=:sname, " +
                "sdesc=:sdesc, indb=:indb, enabled=:enabled, " +
                "namesearch=:namesearch, defaultlayer=:defaultlayer, " +
                "\"intersect\"=:intersect, layerbranch=:layerbranch, analysis=:analysis," +
                " addtomap=:addtomap where id=:id";

        jdbcTemplate.update(sql, field.toMap());
    }

    @Override
    public void delete(String fieldId) {
        Field f = getFieldById(fieldId);

        if (f != null) {
            jdbcTemplate.update("delete from objects where fid='" + f.getId() + "'");
            jdbcTemplate.update("delete from fields where id='" + f.getId() + "'");
        }
    }
}
