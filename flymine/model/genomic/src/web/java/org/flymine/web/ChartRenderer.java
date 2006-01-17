package org.flymine.web;

/*
 * Copyright (C) 2002-2005 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.awt.Color;
import java.awt.RenderingHints;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.swing.Renderer;

import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.Axis;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.entity.StandardEntityCollection;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.renderer.AbstractRenderer;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.servlet.ServletUtilities;
import org.jfree.data.category.DefaultCategoryDataset;

import org.flymine.model.genomic.MicroArrayResult;
import org.flymine.model.genomic.MicroArrayAssay;

import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.web.Constants;
import org.intermine.web.InterMineAction;
import org.intermine.web.InterMineDispatchAction;

import org.apache.commons.collections.keyvalue.MultiKey;
import org.apache.log4j.Logger;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/**
 * 
 * @author Thomas Riley
 */
public class ChartRenderer extends InterMineAction
{
    private static final Logger LOG = Logger.getLogger(ChartRenderer.class);
    private static final Class[] sig = new Class[] {ActionMapping.class, ActionForm.class,
        HttpServletRequest.class, HttpServletResponse.class};
    
    /**
     * First, check for a cached image, otherwise defer to appropriate method.
     * 
     * @param mapping The ActionMapping used to select this instance
     * @param form The optional ActionForm bean for this request (if any)
     * @param request The HTTP request we are processing
     * @param response The HTTP response we are creating
     * @return an ActionForward object defining where control goes next
     * @exception Exception if the application business logic throws
     *  an exception
     */
    public ActionForward execute(ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response)
        throws Exception {
        HttpSession session = request.getSession();
        ServletContext servletContext = session.getServletContext();
        Map graphImageCache = (Map) servletContext.getAttribute(Constants.GRAPH_CACHE);
        String filename = (String) graphImageCache.get(request.getQueryString());
        
        if (filename != null) {
            ServletUtilities.sendTempFile(filename, response);
            return null;
        } else {
            Method method = getClass().getMethod(request.getParameter("method"), sig);
            if (!method.getName().equals("execute")) { // avoid infinite loop
                return (ActionForward)
                    method.invoke(this, new Object[] {mapping, form, request, response});
            } else {
                LOG.error("bad method parameter \"" + request.getParameter("method") + "\"");
                return null;
            }
        }
    }

    /**
     * 
     */
    public ActionForward microarray(
            ActionMapping mapping,
            ActionForm form,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        HttpSession session = request.getSession();
        Map graphImageCache = (Map) session.getServletContext()
            .getAttribute(Constants.GRAPH_CACHE);
        ObjectStore os = (ObjectStore) session.getServletContext()
            .getAttribute(Constants.OBJECTSTORE);
        String experiment = request.getParameter("experiment");
        String gene = request.getParameter("gene");
        
        Results results = MicroArrayHelper.queryMicroArrayResults(experiment, gene, os);
        Iterator iter = results.iterator();
        
        DefaultCategoryDataset xyDataset = new DefaultCategoryDataset();
        
        while (iter.hasNext()) {
            ResultsRow rr = (ResultsRow) iter.next();
            MicroArrayResult result = (MicroArrayResult) rr.get(0);
            Set assays = result.getAssays();
            Iterator assayIter = assays.iterator();
            while (assayIter.hasNext()) {
                String name = ((MicroArrayAssay) assayIter.next()).getName();
                xyDataset.addValue(result.getValue(), "value", name);
            }
        }
        
        CategoryAxis xAxis = new CategoryAxis(null);
        NumberAxis yAxis = new NumberAxis(null);
        BarRenderer renderer = new BarRenderer();
        
        configureXaxis(xAxis, request);
        configureYaxis(yAxis, request);        
        configureRenderer(renderer, request);
        
        CategoryPlot plot = new CategoryPlot(xyDataset, xAxis, yAxis, renderer);
        
        JFreeChart chart = new JFreeChart(null,
                AbstractRenderer.DEFAULT_VALUE_LABEL_FONT, plot, false);
        
        configureChart(chart, request);
        
        int width = Integer.parseInt(request.getParameter("width"));
        int height = Integer.parseInt(request.getParameter("height"));
        
        ChartRenderingInfo info = new ChartRenderingInfo(new StandardEntityCollection());
        String filename = ServletUtilities.saveChartAsPNG(chart, width, height, info, session);
        graphImageCache.put(request.getQueryString(), filename);
        ServletUtilities.sendTempFile(filename, response);
        return null;
    }
    
    protected void configureRenderer(AbstractRenderer renderer, HttpServletRequest request) {
        Color barColor = new Color(100, 149, 237);
        renderer.setSeriesPaint(0, barColor);
        renderer.setSeriesOutlinePaint(0, barColor.darker());
    }
    
    protected void configureXaxis(Axis axis, HttpServletRequest request) {
        if (request.getParameter("method").equals("microarray")) {
            ((CategoryAxis) axis).setMaximumCategoryLabelLines(7);
            axis.setLabelAngle(0);
            //xAxis.setVisible(false);
            ((CategoryAxis) axis).setLowerMargin(0.05);
            axis.setTickLabelsVisible(false);
        }
    }
    
    protected void configureYaxis(Axis axis, HttpServletRequest request) {
        axis.setTickLabelFont(AbstractRenderer.DEFAULT_VALUE_LABEL_FONT.deriveFont(8));
    }
    
    protected void configureChart(JFreeChart chart, HttpServletRequest request) {
        chart.setBackgroundPaint(java.awt.Color.white);
        chart.setAntiAlias(false);
        chart.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF));
    }
}
