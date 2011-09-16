/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ala.spatial.analysis.web;

import java.io.ByteArrayInputStream;
import org.ala.spatial.data.Query;
import org.ala.spatial.util.CommonData;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Filedownload;
import org.zkoss.zul.Messagebox;

/**
 *
 * @author ajay
 */
public class AddToolSamplingComposer extends AddToolComposer {

    int generation_count = 1; 

    @Override
    public void afterCompose() {
        super.afterCompose();

        this.selectedMethod = "Sampling";
        this.totalSteps = 3;

        this.loadAreaLayers();
        this.loadSpeciesLayers();
        this.loadGridLayers(false,true);
        this.updateWindowTitle();
        
    }

    @Override
    public void onLastPanel() {
        super.onLastPanel();
        //this.updateName("Sampling #" + generation_count);
        this.updateName(getMapComposer().getNextAreaLayerName("My Sampling"));
    }

    @Override
    public void onFinish() {
        //super.onFinish();

        System.out.println("Area: " + getSelectedArea());
        System.out.println("Species: " + getSelectedSpecies());
        System.out.println("Layers: " + getSelectedLayers());

        download(null);

    }

    public void download(Event event) {

        try {
            Query query = getSelectedSpecies().newWkt(getSelectedArea());

            //test size        
            if(query.getOccurrenceCount() <= 0) {
                Messagebox.show("No occurrences selected. Please try again", "ALA Spatial Analysis Toolkit - Sampling", Messagebox.OK, Messagebox.ERROR);
                return;
            }
            
            //translate layer names
            String [] layers = null;
            String envlayers = getSelectedLayers();
            if(envlayers.length() > 0) {
                layers = envlayers.split(":");
                for(int i=0;i<layers.length;i++) {
                    layers[i] = CommonData.getLayerFacetName(layers[i]);
                }
            }

            //test for URL download
            String url = query.getDownloadUrl(layers);
            if(url != null) {
                System.out.println("Sending file to user: " + url);

                //TODO: find some way to do this nicely.
                Events.echoEvent("openHTML", getMapComposer(), "Download\n<a href='" + url + "' >click to start download</a>");
                
                //Would clients still treat this as a popup if it were on prod?
                //getMapComposer().openHTML(event)Clients.evalJavaScript("window.open('" + url + "','download','')");

                //TODO: fix logging
                //getMapComposer().updateUserLogAnalysis("Sampling", "species: " + taxon + "; area: " + area, sbenvsel.toString(), CommonData.satServer + "/alaspatial" + slist, pid, "Sampling results for species: " + taxon);
            } else {
                //download data
                byte [] b = query.getDownloadBytes(layers);
                if(b != null) {
                    Filedownload.save(new ByteArrayInputStream(b), "application/zip", query.getName() + ".zip");
                } else {
                    Messagebox.show("Unable to download sample file. Please try again", "ALA Spatial Analysis Toolkit - Sampling", Messagebox.OK, Messagebox.ERROR);
                }
            }

            //
            //add layers to a fields list
            //download

            
//            String taxon = getSelectedSpecies();
//            String sbenvsel = getSelectedLayers();
//            String area = getSelectedArea();
//
//            StringBuffer sbProcessUrl = new StringBuffer();
//            sbProcessUrl.append(CommonData.satServer + "/alaspatial/ws/sampling/process/download?");
//            sbProcessUrl.append("taxonid=" + URLEncoder.encode(taxon, "UTF-8"));
//
//            String envlist = URLEncoder.encode(sbenvsel.toString(), "UTF-8");
//            if(envlist == null || envlist.trim().length() == 0) {
//                envlist = "none";
//            }
//            sbProcessUrl.append("&envlist=" + envlist);
//
//            HttpClient client = new HttpClient();
//            PostMethod get = new PostMethod(sbProcessUrl.toString());
//            get.addParameter("area", area);
//
//            get.addRequestHeader("Accept", "text/plain");
//
//            int result = client.executeMethod(get);
//            String slist = get.getResponseBodyAsString();
//
//            System.out.println("Got response from SamplingWSController: \n" + slist);
//
//            if (slist.equalsIgnoreCase("")) {
//                Messagebox.show("Unable to download sample file. Please try again", "ALA Spatial Analysis Toolkit - Sampling", Messagebox.OK, Messagebox.ERROR);
//            } else {
//                System.out.println("Sending file to user: " + CommonData.satServer + "/alaspatial" + slist);
//
//                URL url = new URL(CommonData.satServer + "/alaspatial" + slist);
//                Filedownload.save(url.openStream(), "application/zip",url.getFile());
//                getMapComposer().updateUserLogAnalysis("Sampling", "species: " + taxon + "; area: " + area, sbenvsel.toString(), CommonData.satServer + "/alaspatial" + slist, pid, "Sampling results for species: " + taxon);
//            }
//
            this.detach();

        } catch (Exception e) {
            System.out.println("Exception calling sampling.download:");
            e.printStackTrace(System.out);
        }
    }


}