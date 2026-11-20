package com.controlj.addon.heatmap.servlet;

import com.controlj.addon.heatmap.util.SettingsManager;
import org.json.JSONObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

@WebServlet("/settings")
public class SettingsServlet extends HttpServlet {
    
    @Override
    public void init() {
        // Инициализируем SettingsManager с путём к данным аддона
        String dataDir = (String) getServletContext().getAttribute("webapp.data.dir");
        if (dataDir == null) {
            dataDir = System.getProperty("webapp.data.dir");
        }
        if (dataDir != null) {
            SettingsManager.init(dataDir);
            System.out.println("[SettingsServlet] SettingsManager initialized with dir: " + dataDir);
        } else {
            System.err.println("[SettingsServlet] Could not determine webapp.data.dir");
        }
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        resp.setContentType("application/json; charset=UTF-8");
        try {
            String action = req.getParameter("action");
            
            if ("load".equals(action)) {
                // Загрузка текущих настроек
                JSONObject settings = new JSONObject();
                settings.put("minDaysForMedian", SettingsManager.getMinDaysForMedian());
                settings.put("zeroThreshold", SettingsManager.getZeroThreshold());
                settings.put("statusTrendName", SettingsManager.getStatusTrendName());
                settings.put("statusErrorOnZero", SettingsManager.isStatusErrorOnZero());
                settings.put("frozenRunHours", SettingsManager.getFrozenRunHours());
                settings.put("anomalyCorrectionEnabled", SettingsManager.isAnomalyCorrectionEnabled());
                settings.put("debugLoggingEnabled", SettingsManager.isDebugLoggingEnabled());
                settings.put("resetThreshold", SettingsManager.getResetThreshold());
                settings.put("dataDir", SettingsManager.getDataDir());
                
                PrintWriter out = resp.getWriter();
                out.print(settings.toString());
            } else {
                sendError(resp, "Unknown action: " + action);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            try {
                sendError(resp, e.getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        resp.setContentType("application/json; charset=UTF-8");
        try {
            String action = req.getParameter("action");
            
            if ("save".equals(action)) {
                // Сохранение настроек
                String minDays = req.getParameter("minDaysForMedian");
                String zeroThreshold = req.getParameter("zeroThreshold");
                String anomalyCorrection = req.getParameter("anomalyCorrectionEnabled");
                String debugLogging = req.getParameter("debugLoggingEnabled");
                String resetThreshold = req.getParameter("resetThreshold");
                String statusTrendName = req.getParameter("statusTrendName");
                String statusErrorOnZero = req.getParameter("statusErrorOnZero");
                String frozenRunHours = req.getParameter("frozenRunHours");
                
                if (minDays != null) {
                    SettingsManager.setMinDaysForMedian(Integer.parseInt(minDays));
                }
                if (zeroThreshold != null) {
                    SettingsManager.setZeroThreshold(Double.parseDouble(zeroThreshold));
                }
                if (anomalyCorrection != null) {
                    SettingsManager.setAnomalyCorrectionEnabled(Boolean.parseBoolean(anomalyCorrection));
                }
                if (debugLogging != null) {
                    SettingsManager.setDebugLoggingEnabled(Boolean.parseBoolean(debugLogging));
                }
                if (resetThreshold != null) {
                    SettingsManager.setResetThreshold(Double.parseDouble(resetThreshold));
                }
                if (statusTrendName != null) {
                    SettingsManager.setStatusTrendName(statusTrendName);
                }
                if (statusErrorOnZero != null) {
                    SettingsManager.setStatusErrorOnZero(Boolean.parseBoolean(statusErrorOnZero));
                }
                if (frozenRunHours != null) {
                    try {
                        SettingsManager.setFrozenRunHours(Integer.parseInt(frozenRunHours.trim()));
                    } catch (NumberFormatException ignored) {
                        // некорректное значение — оставляем как было
                    }
                }
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("message", "Settings saved successfully");
                result.put("dataDir", SettingsManager.getDataDir());
                
                PrintWriter out = resp.getWriter();
                out.print(result.toString());
                
            } else {
                sendError(resp, "Unknown action: " + action);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            try {
                sendError(resp, e.getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    private void sendError(HttpServletResponse resp, String message) throws Exception {
        resp.setContentType("application/json");
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        JSONObject error = new JSONObject();
        error.put("error", message);
        resp.getWriter().print(error.toString());
    }
}