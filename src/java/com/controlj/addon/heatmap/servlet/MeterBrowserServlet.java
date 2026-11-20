package com.controlj.addon.heatmap.servlet;

import com.controlj.green.addonsupport.InvalidConnectionRequestException;
import com.controlj.green.addonsupport.access.*;
import com.controlj.green.addonsupport.access.aspect.TrendSource;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@WebServlet("/browse")
public class MeterBrowserServlet extends HttpServlet {
    private static final ConcurrentHashMap<String, List<MeterInfo>> cache = new ConcurrentHashMap<>();
    private static final String TREND_NAME = "cons_total_net_tn";
    private static final String METER_PATTERN = "_e1";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String forceRefresh = req.getParameter("refresh");
        List<MeterInfo> meters = cache.get(TREND_NAME);
        if (meters == null || "true".equalsIgnoreCase(forceRefresh)) {
            try {
                meters = scanMeters();
                cache.put(TREND_NAME, meters);
            } catch (Exception e) {
                sendError(resp, "Scan failed: " + e.getMessage());
                return;
            }
        }

        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();
        JSONArray result = new JSONArray();
        for (MeterInfo m : meters) {
            JSONObject obj = new JSONObject();
            obj.put("name", m.name);
            obj.put("id", m.trendId);
            obj.put("path", m.programPath);
            result.put(obj);
        }
        out.print(result.toString());
    }

    private List<MeterInfo> scanMeters() throws Exception {
        List<MeterInfo> result = new ArrayList<>();
        SystemConnection conn = DirectAccess.getDirectAccess().getRootSystemConnection();
        conn.runReadAction(new ReadAction() {
            @Override
            public void execute(SystemAccess access) throws Exception {
                Tree geo = access.getTree(SystemTree.Geographic);
                Location root = geo.getRoot();
                scanLocation(root, result);
            }
        });
        return result;
    }

    private void scanLocation(Location loc, List<MeterInfo> out) throws Exception {
        String name = loc.getReferenceName();
        if (name != null && name.contains(METER_PATTERN)) {
            try {
                // Ищем потомка с именем TREND_NAME
                Location trendPoint = loc.getDescendant(TREND_NAME);
                if (trendPoint != null && trendPoint.hasAspect(TrendSource.class)) {
                    String trendId = trendPoint.getPersistentLookupString(true); // canonical path
                    MeterInfo info = new MeterInfo();
                    info.name = name;
                    info.trendId = trendId;
                    info.programPath = loc.getReferenceName(); // или loc.toString()
                    out.add(info);
                }
            } catch (UnresolvableException e) {
                // нет потомка – пропускаем
            }
        }
        // Рекурсивный обход дочерних локаций
        for (Location child : loc.getChildren()) {
            scanLocation(child, out);
        }
    }

    private static class MeterInfo {
        String name;
        String trendId;
        String programPath;
    }

    private void sendError(HttpServletResponse resp, String message) throws IOException {
        resp.setContentType("application/json");
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        JSONObject error = new JSONObject();
        error.put("error", message);
        resp.getWriter().print(error.toString());
    }
}