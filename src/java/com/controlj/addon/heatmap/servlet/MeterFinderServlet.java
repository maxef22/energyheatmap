package com.controlj.addon.heatmap.servlet;

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

@WebServlet("/meters/find")
public class MeterFinderServlet extends HttpServlet {
    private static final String TREND_NAME = "cons_total_net_tn";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathParam = req.getParameter("path");
        String effectivePath = (pathParam == null || pathParam.isEmpty()) ? "#" : pathParam;
        String masksParam = req.getParameter("masks");
        final String[] masks;
        if (masksParam == null || masksParam.isEmpty()) {
            masks = new String[]{"_e1"};
        } else {
            masks = masksParam.split(",");
        }

        try {
            final SystemConnection conn = DirectAccess.getDirectAccess().getRootSystemConnection();
            final List<MeterInfo> meters = new ArrayList<>();

            conn.runReadAction(new ReadAction() {
                @Override
                public void execute(SystemAccess access) throws Exception {
                    Tree geo = access.getTree(SystemTree.Geographic);
                    Location rootLocation;
                    if ("#".equals(effectivePath) || effectivePath.isEmpty()) {
                        rootLocation = geo.getRoot();
                    } else {
                        rootLocation = geo.resolve(effectivePath);
                    }
                    scanLocation(rootLocation, masks, meters);
                }
            });

            JSONArray result = new JSONArray();
            for (MeterInfo m : meters) {
                JSONObject obj = new JSONObject();
                obj.put("name", m.name);
                obj.put("path", m.path);
                obj.put("displayPath", m.displayPath);
                String cleanPath = m.path.replaceAll("^#+", "");
                String trendId = "ABSPATH:1:#" + cleanPath + "/" + TREND_NAME;
                obj.put("id", trendId);
                result.put(obj);
            }
            resp.setContentType("application/json; charset=UTF-8");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter out = resp.getWriter();
            out.print(result.toString());
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().print("{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }

    private void scanLocation(Location loc, String[] masks, List<MeterInfo> out) throws Exception {
        String name = loc.getReferenceName();
        if (name != null) {
            boolean matched = false;
            for (String mask : masks) {
                if (name.contains(mask.trim())) {
                    matched = true;
                    break;
                }
            }
            if (matched) {
                try {
                    Location trendPoint = loc.getDescendant(TREND_NAME);
                    if (trendPoint != null && trendPoint.hasAspect(TrendSource.class)) {
                        MeterInfo info = new MeterInfo();
                        info.name = name;
                        String fullPath = loc.toString();
                        int hashIndex = fullPath.indexOf('#');
                        if (hashIndex != -1) {
                            info.path = fullPath.substring(hashIndex);
                        } else {
                            info.path = "#" + name;
                        }
                        // Получаем отображаемый иерархический путь
                        info.displayPath = loc.getDisplayPath();
                        out.add(info);
                    }
                } catch (UnresolvableException e) {
                    // нет тренда – пропускаем
                }
            }
        }
        for (Location child : loc.getChildren()) {
            scanLocation(child, masks, out);
        }
    }

    private static class MeterInfo {
        String name;
        String path;
        String displayPath;
    }
}