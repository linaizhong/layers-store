package org.ala.spatial.analysis.web;

import au.org.emii.portal.composer.UtilityComposer;
import au.org.emii.portal.settings.SettingsSupplementary;
import au.org.emii.portal.util.LayerUtilities;
import java.io.Writer;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.ala.spatial.data.Query;
import org.ala.spatial.data.QueryUtil;
import org.ala.spatial.util.CommonData;
import org.ala.spatial.data.SolrQuery;
import org.ala.spatial.util.SelectedArea;
import org.ala.spatial.util.Util;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.zkoss.zhtml.Filedownload;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zul.Button;
import org.zkoss.zul.Label;
import org.apache.log4j.Logger;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Window;

/**
 *
 * @author adam
 */
public class FilteringResultsWCController extends UtilityComposer {

    private static Logger logger = Logger.getLogger(FilteringResultsWCController.class);
    public Button mapspecies;
    public Label results_label2_occurrences;
    public Label results_label2_species;
    public Label sdLabel;
    String[] speciesDistributionText = null;
    Window window = null;
    public String[] results = null;
    public String pid;
    //String shape;
    private SettingsSupplementary settingsSupplementary = null;
    int results_count = 0;
    int results_count_occurrences = 0;
    boolean addedListener = false;
    Label lblArea;
    Label lblBiostor;
    //String reportArea = null;
    SelectedArea selectedArea = null;
    String areaName = "Area Report";
    String areaDisplayName = "Area Report";
    String areaSqKm = null;
    double[] boundingBox = null;
    HashMap<String, String> data = new HashMap<String, String>();

    public void setReportArea(SelectedArea sa, String name, String displayname, String areaSqKm, double[] boundingBox) {
        selectedArea = sa;
        areaName = name;
        areaDisplayName = displayname;
        this.areaSqKm = areaSqKm;
        this.boundingBox = boundingBox;
        setTitle(displayname);

        if (name.equals("Current extent")) {
            addListener();
        }

        try {
            refreshCount();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterCompose() {
        super.afterCompose();

//        try {
//            refreshCount();
//        }catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    @Override
    public void detach() {
        getMapComposer().getLeftmenuSearchComposer().removeViewportEventListener("filteringResults");

        super.detach();
    }

    @Override
    public void redraw(Writer out) throws java.io.IOException {
        super.redraw(out);

        if (selectedArea != null) {
            setUpdatingCount(true);
        }
    }

    void addListener() {
        if (!addedListener) {
            addedListener = true;
            //register for viewport changes
            EventListener el = new EventListener() {

                public void onEvent(Event event) throws Exception {
                    selectedArea = new SelectedArea(null, getMapComposer().getViewArea());
                    refreshCount();
                }
            };
            getMapComposer().getLeftmenuSearchComposer().addViewportEventListener("filteringResults", el);
        }
    }

    void setUpdatingCount(boolean set) {
        if (set) {
            results_label2_occurrences.setValue("updating...");
            results_label2_species.setValue("updating...");
            sdLabel.setValue("updating...");
            lblArea.setValue("updating...");
            lblBiostor.setValue("updating...");
        }
    }

//    public void populateList() {
//        try {
//            StringBuffer sbProcessUrl = new StringBuffer();
//            sbProcessUrl.append("/filtering/apply");
//            sbProcessUrl.append("/pid/" + URLEncoder.encode(pid, "UTF-8"));
//            sbProcessUrl.append("/species/list");
//
//            String out = postInfo(sbProcessUrl.toString());
//            //remove trailing ','
//            if (out.length() > 0 && out.charAt(out.length() - 1) == ',') {
//                out = out.substring(0, out.length() - 1);
//            }
//            results = out.split("\\|");
//            java.util.Arrays.sort(results);
//
//            if (results.length == 0 || results[0].trim().length() == 0) {
//                //results_label2_species.setValue("0");
//                //results_label2_occurrences.setValue("0");
//                data.put("speciesCount", "0");
//                data.put("occurrencesCount", "0");
//                mapspecies.setVisible(false);
//                results = null;
//                return;
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    boolean isTabOpen() {
        return true; //getMapComposer().getPortalSession().getCurrentNavigationTab() == PortalSession.LINK_TAB;
    }

    public void refreshCount() {
        //check if tab is open
//        if (!isTabOpen() || !updateParameters()) {
//            return;
//        }

        setUpdatingCount(true);

        startRefreshCount();

        Events.echoEvent("finishRefreshCount", this, null);
    }
    CountDownLatch counter = null;
    long start = 0;
    Thread biostorThread;

    void startRefreshCount() {
        //countdown includes; intersectWithSpecies, calcuateArea, counts
        counter = new CountDownLatch(3);

        Thread t1 = new Thread() {

            @Override
            public void run() {
                setPriority(Thread.MIN_PRIORITY);
                intersectWithSpeciesDistributions();
                decCounter();
            }
        };

        Thread t2 = new Thread() {

            @Override
            public void run() {
                setPriority(Thread.MIN_PRIORITY);
                calculateArea();
                decCounter();
            }
        };

        biostorThread = new Thread() {

            @Override
            public void run() {
                setPriority(Thread.MIN_PRIORITY);
                biostor();
            }
        };

        Thread t4 = new Thread() {

            @Override
            public void run() {
                setPriority(Thread.MIN_PRIORITY);
                counts();
                decCounter();
            }
        };

        t1.start();
        t2.start();
        biostorThread.start();
        t4.start();

        long start = System.currentTimeMillis();

        getMapComposer().updateUserLogAnalysis("species count", "area: " + selectedArea.getWkt(), "", "species list in area");
    }

    public void finishRefreshCount() {
        try {
            counter.await();
        } catch (InterruptedException ex) {
            java.util.logging.Logger.getLogger(FilteringResultsWCController.class.getName()).log(Level.SEVERE, null, ex);
        }

        //wait up to 5s for biostor
        while (biostorThread.isAlive() && (System.currentTimeMillis() - start) < 5000) {
        }

        //terminate wait on biostor if still active
        if (biostorThread.isAlive()) {
            biostorThread.interrupt();
            data.put("biostor", "na");
            Clients.evalJavaScript("displayBioStorCount('biostorrow','na');");
        }

        //set labels
        sdLabel.setValue(data.get("intersectWithSpeciesDistributions"));
        lblBiostor.setValue(data.get("biostor"));
        lblArea.setValue(data.get("area"));
        results_label2_species.setValue(data.get("speciesCount"));
        results_label2_occurrences.setValue(data.get("occurrencesCount"));

        //underline?
        if (isNumberGreaterThanZero(lblBiostor.getValue())) {
            lblBiostor.setSclass("underline");
        } else {
            lblBiostor.setSclass("");
        }
        if (isNumberGreaterThanZero(sdLabel.getValue())) {
            sdLabel.setSclass("underline");
        } else {
            sdLabel.setSclass("");
        }
        if (isNumberGreaterThanZero(results_label2_species.getValue())) {
            results_label2_species.setSclass("underline");
        } else {
            results_label2_species.setSclass("");
        }
        if (isNumberGreaterThanZero(results_label2_occurrences.getValue())) {
            results_label2_occurrences.setSclass("underline");
        } else {
            results_label2_occurrences.setSclass("");
        }

        // toggle the map button
        if (results_count > 0 && results_count_occurrences <= settingsSupplementary.getValueAsInt("max_record_count_map")) {
            mapspecies.setVisible(true);
        } else {
            mapspecies.setVisible(false);
        }
    }

    void decCounter() {
        if (counter != null) {
            counter.countDown();
        }
    }

    void counts() {
        try {
            Query sq = QueryUtil.queryFromSelectedArea(null, selectedArea, false);

            results_count = sq.getSpeciesCount();
            results_count_occurrences = sq.getOccurrenceCount();

            //setUpdatingCount(false);

            if (results_count == 0) {
                //results_label.setValue("no species in active area");
                //results_label2_species.setValue("0");
                //results_label2_occurrences.setValue("0");
                data.put("speciesCount", "0");
                data.put("occurrencesCount", "0");
                mapspecies.setVisible(false);
                results = null;
                return;
            }

            //results_label2_species.setValue(String.format("%,d", results_count));
            //results_label2_occurrences.setValue(String.format("%,d", results_count_occurrences));
            data.put("speciesCount", String.format("%,d", results_count));
            data.put("occurrencesCount", String.format("%,d", results_count_occurrences));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void onClick$results_label2_species() {
        SpeciesListEvent sle = new SpeciesListEvent(getMapComposer(), areaName, 1);
        try {
            sle.onEvent(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onClick$results_label2_occurrences() {
        SamplingEvent sle = new SamplingEvent(getMapComposer(), null, areaName, null, 2);
        try {
            sle.onEvent(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onClick$mapspecies() {
        //getMapComposer().addToSession("Occurrences in Active area", "lsid=aa");
        onMapSpecies(null);
    }

    public void onMapSpecies(Event event) {
        try {
            SelectedArea sa = null;
            if (!areaName.equalsIgnoreCase("Current extent")) {
                sa = selectedArea;
            } else {
                sa = new SelectedArea(null, getMapComposer().getViewArea());
            }

            Query query = QueryUtil.queryFromSelectedArea(null, sa, true);

            String activeAreaLayerName = getMapComposer().getNextActiveAreaLayerName(areaDisplayName);
            getMapComposer().mapSpecies(query, activeAreaLayerName, "species", -1, LayerUtilities.SPECIES, null, -1);

            //getMapComposer().updateUserLogAnalysis("Sampling", query.getName(), "", CommonData.satServer + "/" + sbProcessUrl.toString(), pid, "map species in area");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    private String getInfo(String urlPart) {
//        try {
//            HttpClient client = new HttpClient();
//
//            GetMethod get = new GetMethod(CommonData.satServer + "/ws" + urlPart); // testurl
//            get.addRequestHeader("Accept", "application/json, text/javascript, */*");
//
//            int result = client.executeMethod(get);
//
//            //TODO: confirm result
//            String slist = get.getResponseBodyAsString();
//
//            return slist;
//        } catch (Exception ex) {
//            //TODO: error message
//            System.out.println("getInfo.error:");
//            ex.printStackTrace(System.out);
//        }
//        return null;
//    }

//    private String postInfo(String urlPart) {
//        try {
//            HttpClient client = new HttpClient();
//
//            PostMethod post = new PostMethod(CommonData.satServer + "/ws" + urlPart); // testurl
//
//            post.addRequestHeader("Accept", "application/json, text/javascript, */*");
//            post.addParameter("area", shape);
//
//            System.out.println("satServer:" + CommonData.satServer + " ** postInfo:" + urlPart + " ** " + shape);
//
//            int result = client.executeMethod(post);
//
//            //TODO: confirm result
//            String slist = post.getResponseBodyAsString();
//
//            return slist;
//        } catch (Exception ex) {
//            //TODO: error message
//            System.out.println("getInfo.error:");
//            ex.printStackTrace(System.out);
//        }
//        return null;
//    }

//    boolean updateParameters() {
//        //extract 'shape' and 'pid' from composer
//        String area = reportArea;
//
//        if (area.contains("ENVELOPE(")) {
//            shape = "none";
//            pid = area.substring(9, area.length() - 1);
//            return true;
//        } else {
//            pid = "none";
//            if (shape == null || !shape.equalsIgnoreCase(area)) {
//                shape = area;
//                return true;
//            } else {
//                return false;
//            }
//        }
//    }
    static public void open(SelectedArea sa, String name, String displayName, String areaSqKm, double[] boundingBox) {
        FilteringResultsWCController win = (FilteringResultsWCController) Executions.createComponents(
                "/WEB-INF/zul/AnalysisFilteringResults.zul", null, null);
        try {
            win.doOverlapped();
            win.setPosition("center");
            win.setReportArea(sa, name, displayName, areaSqKm, boundingBox);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void refreshCount(int newCount, int newOccurrencesCount) {
        results_count = newCount;
        results_count_occurrences = newOccurrencesCount;
        if (results_count == 0) {
            //results_label2_species.setValue(String.format("%,d", results_count));
            //results_label2_occurrences.setValue(String.format("%,d", results_count_occurrences));
            data.put("occurrencesCount", String.format("%,d", results_count_occurrences));
            data.put("speciesCount", String.format("%,d", results_count));
            results = null;
        }

        //results_label2_species.setValue(String.format("%,d", results_count));
        //results_label2_occurrences.setValue(String.format("%,d", results_count_occurrences));
        data.put("occurrencesCount", String.format("%,d", results_count_occurrences));
        data.put("speciesCount", String.format("%,d", results_count));
        setUpdatingCount(false);

        // toggle the map button
        if (results_count > 0 && results_count_occurrences <= settingsSupplementary.getValueAsInt("max_record_count_map")) {
            mapspecies.setVisible(true);
        } else {
            mapspecies.setVisible(false);
        }
    }

    public void intersectWithSpeciesDistributions() {
        try {
            String area = selectedArea.getWkt();
            
            StringBuffer sbProcessUrl = new StringBuffer();
            sbProcessUrl.append("/distributions");

            HttpClient client = new HttpClient();
            PostMethod post = new PostMethod(CommonData.layersServer + sbProcessUrl.toString()); // testurl
            post.addParameter("wkt", area);
            post.addRequestHeader("Accept", "application/json, text/javascript, */*");
            int result = client.executeMethod(post);
            if (result == 200) {
                String txt = post.getResponseBodyAsString();
                JSONArray ja = JSONArray.fromObject(txt);
                if (ja == null || ja.size() == 0) {
                    data.put("intersectWithSpeciesDistributions", "0");
                    speciesDistributionText = null;
                } else {
                    String[] lines = new String[ja.size() + 1];
                    lines[0] = "SPCODE,SCIENTIFIC_NAME,AUTHORITY_FULL,COMMON_NAME,FAMILY,GENUS_NAME,SPECIFIC_NAME,MIN_DEPTH,MAX_DEPTH,METADATA_URL,LSID";
                    for (int i = 0; i < ja.size(); i++) {
                        JSONObject jo = ja.getJSONObject(i);
                        String spcode = jo.containsKey("spcode") ? jo.getString("spcode") : "";
                        String scientific = jo.containsKey("scientific") ? jo.getString("scientific") : "";
                        String auth = jo.containsKey("authority_") ? jo.getString("authority_") : "";
                        String common = jo.containsKey("common_nam") ? jo.getString("common_nam") : "";
                        String family = jo.containsKey("family") ? jo.getString("family") : "";
                        String genus = jo.containsKey("genus") ? jo.getString("genus") : "";
                        String name = jo.containsKey("specific_n") ? jo.getString("specific_n") : "";
                        String min = jo.containsKey("min_depth") ? jo.getString("min_depth") : "";
                        String max = jo.containsKey("max_depth") ? jo.getString("max_depth") : "";
                        //String p = jo.containsKey("pelagic_fl")?jo.getString("pelagic_fl"):"";
                        String md = jo.containsKey("metadata_u") ? jo.getString("metadata_u") : "";
                        String lsid = jo.containsKey("lsid") ? jo.getString("lsid") : "";

                        StringBuilder sb = new StringBuilder();
                        sb.append(spcode).append(",");
                        sb.append(wrap(scientific)).append(",");
                        sb.append(wrap(auth)).append(",");
                        sb.append(wrap(common)).append(",");
                        sb.append(wrap(family)).append(",");
                        sb.append(wrap(genus)).append(",");
                        sb.append(wrap(name)).append(",");
                        sb.append(min).append(",");
                        sb.append(max).append(",");
                        sb.append(wrap(md)).append(",");
                        sb.append(wrap(lsid));

                        lines[i + 1] = sb.toString();
                    }

                    data.put("intersectWithSpeciesDistributions", String.format("%,d", lines.length - 1));
                    speciesDistributionText = lines;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    String wrap(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public void onClick$sdLabel(Event event) {
        int c = 0;
        try {
            c = Integer.parseInt(sdLabel.getValue());
        } catch (Exception e) {
        }
        if (c > 0 && speciesDistributionText != null) {
            DistributionsWCController window = (DistributionsWCController) Executions.createComponents("WEB-INF/zul/AnalysisDistributionResults.zul", this, null);

            try {
                window.doModal();
                window.init(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void onClick$sdDownload(Event event) {
        String spid = pid;
        if (spid == null || spid.equals("none")) {
            spid = String.valueOf(System.currentTimeMillis());
        }

        SimpleDateFormat date = new SimpleDateFormat("yyyyMMdd");
        String sdate = date.format(new Date());

        StringBuilder sb = new StringBuilder();
        for (String s : speciesDistributionText) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(s);
        }
        Filedownload.save(sb.toString(), "text/plain", "Species_distributions_" + sdate + "_" + spid + ".csv");
    }

    private void calculateArea() {
        if (areaSqKm != null) {
            data.put("area", areaSqKm);
            speciesDistributionText = null;
            return;
        }

        try {
            double totalarea = Util.calculateArea(selectedArea.getWkt());
            DecimalFormat df = new DecimalFormat("###,###.##");

            //lblArea.setValue(String.format("%,d", (int) (totalarea / 1000 / 1000)));
            //data.put("area",String.format("%,f", (totalarea / 1000 / 1000)));
            data.put("area", df.format(totalarea / 1000 / 1000));

        } catch (Exception e) {
            System.out.println("Error in calculateArea");
            e.printStackTrace(System.out);
            data.put("area", "");
        }
    }

    public void onClick$btnCancel(Event event) {
        this.detach();
    }
    String biostorHtml = null;

    private void biostor() {
        try {
            String area = selectedArea.getWkt();

            Pattern coord = Pattern.compile("[+-]?[0-9]*\\.?[0-9]* [+-]?[0-9]*\\.?[0-9]*");
            Matcher matcher = coord.matcher(area);

            double lat1 = 0;
            double lat2 = 0;
            double long1 = 0;
            double long2 = 0;
            boolean first = true;
            while (matcher.find()) {
                String[] p = matcher.group().split(" ");
                double[] d = {Double.parseDouble(p[0]), Double.parseDouble(p[1])};

                if (first || long1 > d[0]) {
                    long1 = d[0];
                }
                if (first || long2 < d[0]) {
                    long2 = d[0];
                }
                if (first || lat1 > d[1]) {
                    lat1 = d[1];
                }
                if (first || lat2 < d[1]) {
                    lat2 = d[1];
                }

                first = false;
            }

            String biostorurl = "http://biostor.org/bounds.php?";
            biostorurl += "bounds=" + long1 + "," + lat1 + "," + long2 + "," + lat2;

            HttpClient client = new HttpClient();
            client.getHttpConnectionManager().getParams().setConnectionTimeout(5000);
            GetMethod get = new GetMethod(biostorurl);
            get.addRequestHeader("Accept", "application/json, text/javascript, */*");
            int result = client.executeMethod(get);

            biostorHtml = null;
            if (result == HttpStatus.SC_OK) {
                String slist = get.getResponseBodyAsString();
                if (slist != null) {

                    JSONArray list = JSONObject.fromObject(slist).getJSONArray("list");
                    StringBuilder sb = new StringBuilder();
                    sb.append("<ol>");
                    for (int i = 0; i < list.size(); i++) {
                        sb.append("<li>");
                        sb.append("<a href=\"http://biostor.org/reference/");
                        sb.append(list.getJSONObject(i).getString("id"));
                        sb.append("\" target=\"_blank\">");
                        sb.append(list.getJSONObject(i).getString("title"));
                        sb.append("</li>");
                    }
                    sb.append("</ol>");

                    if (list.size() > 0) {
                        biostorHtml = sb.toString();
                    }

//                $.getJSON(proxy_script + biostorurl, function(data){
//                            var html = '<ol>';
//                            for(var i=0, item; item=data.list[i]; i++) {
//                                html += '<li>' + '<a href="http://biostor.org/reference/' + item.id + '" target="_blank">' + item.title + '</a></li>';
//                            }
//                            html += '</ol>';
//                            parent.displayHTMLInformation("biostormsg","<u>" + data.list.length + "</u>");
//                            parent.displayHTMLInformation('biostorlist',html);
//                        });
                    //lblBiostor.setValue(String.valueOf(list.size()));
                    data.put("biostor", String.valueOf(list.size()));
                }
            } else {
                //lblBiostor.setValue("BioStor currently down");
                data.put("biostor", "Biostor currently down");
            }

        } catch (Exception e) {
            e.printStackTrace(System.out);
            //lblBiostor.setValue("BioStor currently down");
            data.put("biostor", "Biostor currently down");
        }
    }

    public void onClick$lblBiostor(Event event) {
        if (biostorHtml != null) {
            Event ev = new Event("onClick", this, "Biostor Documents\n" + biostorHtml);
            getMapComposer().openHTML(ev);
        }
    }

    private boolean isNumberGreaterThanZero(String value) {
        boolean ret = false;
        try {
            ret = Double.parseDouble(value.replace(",", "")) > 0;
        } catch (Exception e) {
        }

        return ret;
    }
}