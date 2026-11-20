package com.controlj.addon.heatmap.servlet;

import com.controlj.green.addonsupport.access.*;
import com.controlj.green.addonsupport.access.aspect.TrendSource;
import com.controlj.green.addonsupport.access.trend.*;
import com.controlj.addon.heatmap.util.AnomalyDetector;
import com.controlj.addon.heatmap.util.SettingsManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

@WebServlet("/report/calculated-percent")
public class CalculatedPercentReportServlet extends HttpServlet {
    
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
    
    private static final String[] GROUPS = {
        "Общий E1000",
        "Мясной цех E1300",
        "Пекарня E1200",
        "Рыбный цех E1400",
        "Ресторан, кулинария E1500",
        "Мясная лавка, фудкорд, хлебная лавка E1600",
        "Вентиляция E1800",
        "Кондиционирование E1850",
        "Промышленный холод E1100",
        "Освещение внутреннее E1700",
        "Освещение наружное E1750"
    };
    
    private static final Map<String, String> GROUP_MASKS = new HashMap<>();
    static {
        GROUP_MASKS.put("Общий E1000", "e10");
        GROUP_MASKS.put("Мясной цех E1300", "e13");
        GROUP_MASKS.put("Пекарня E1200", "e12");
        GROUP_MASKS.put("Рыбный цех E1400", "e14");
        GROUP_MASKS.put("Ресторан, кулинария E1500", "e15");
        GROUP_MASKS.put("Мясная лавка, фудкорд, хлебная лавка E1600", "e16");
        GROUP_MASKS.put("Вентиляция E1800", "e180");
        GROUP_MASKS.put("Кондиционирование E1850", "e185");
        GROUP_MASKS.put("Промышленный холод E1100", "e11");
        GROUP_MASKS.put("Освещение внутреннее E1700", "e170");
        GROUP_MASKS.put("Освещение наружное E1750", "e175");
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        resp.setContentType("application/json; charset=UTF-8");
        try {
            String hypermarketPath = req.getParameter("path");
            String monthsParam = req.getParameter("months");
            
            if (hypermarketPath == null || hypermarketPath.trim().isEmpty()) {
                sendError(resp, "Missing 'path' parameter");
                return;
            }
            
            String pathForLookup = hypermarketPath;
            if (!pathForLookup.startsWith("ABSPATH:1:")) {
                if (!pathForLookup.startsWith("#")) {
                    pathForLookup = "#" + pathForLookup;
                }
                pathForLookup = "ABSPATH:1:" + pathForLookup;
            }
            
            int monthsCount = 1;
            if (monthsParam != null && !monthsParam.isEmpty()) {
                try {
                    monthsCount = Integer.parseInt(monthsParam);
                    if (monthsCount < 1) monthsCount = 1;
                    if (monthsCount > 24) monthsCount = 24;
                } catch (NumberFormatException e) {
                    monthsCount = 1;
                }
            }
            
            System.out.println("CalculatedPercentReportServlet: monthsCount=" + monthsCount);
            
            Calendar cal = Calendar.getInstance();
            int currentYear = cal.get(Calendar.YEAR);
            int currentMonth = cal.get(Calendar.MONTH) + 1;
            
            int lastFullMonth = currentMonth - 1;
            int lastFullYear = currentYear;
            if (lastFullMonth <= 0) {
                lastFullMonth = 12;
                lastFullYear--;
            }
            
            int startYear = lastFullYear;
            int startMonth = lastFullMonth - (monthsCount - 1);
            while (startMonth <= 0) {
                startMonth += 12;
                startYear--;
            }
            
            System.out.println("Period: " + startYear + "-" + startMonth + " to " + lastFullYear + "-" + lastFullMonth);
            
            SystemConnection conn;
            try {
                // Отчёт вызывается из браузера оператора — читаем тренды с его правами.
                conn = DirectAccess.getDirectAccess().getUserSystemConnection(req);
            } catch (Exception e) {
                System.out.println("Report: пользовательское соединение недоступно (" + e.getMessage()
                        + "), используем корневое");
                conn = DirectAccess.getDirectAccess().getRootSystemConnection();
            }
            String statusTrendName = SettingsManager.getStatusTrendName();
            System.out.println("Status trend: " + statusTrendName);
            
            JSONArray result = new JSONArray();
            Set<String> processedGroups = new HashSet<>();
            
            for (String group : GROUPS) {
                if (processedGroups.contains(group)) {
                    System.out.println("Skipping duplicate group: " + group);
                    continue;
                }
                processedGroups.add(group);
                
                String mask = GROUP_MASKS.get(group);
                if (mask == null) continue;
                
                List<MeterInfo> meters = findMeters(conn, pathForLookup, mask);
                
                System.out.println("========================================");
                System.out.println("GROUP: " + group);
                System.out.println("MASK: " + mask);
                System.out.println("Meters found: " + meters.size());
                for (MeterInfo m : meters) {
                    System.out.println("  -> " + m.name);
                    System.out.println("     Data (net): " + m.dataId);
                }
                System.out.println("========================================");
                
                JSONObject groupData = new JSONObject();
                groupData.put("group", group);
                groupData.put("mask", mask);
                
                JSONArray monthlyData = new JSONArray();
                List<String> errors = new ArrayList<>();
                List<String> diag = new ArrayList<>();
                int year = startYear;
                int month = startMonth;
                
                if (!meters.isEmpty()) {
                    for (int i = 0; i < monthsCount; i++) {
                        double totalConsumption = 0;        // наработка группы
                        double measuredConsumption = 0;     // измеренный объём
                        double calculatedConsumption = 0;   // расчётный объём (границы месяца)
                        double resetFilledConsumption = 0;  // восстановлено из-за сбросов счётчиков
                        int calculatedMeters = 0;
                        int boundaryGapMeters = 0;
                        int noStatusMeters = 0;
                        int metersFailed = 0;
                        int totalMeters = meters.size();
                        
                        for (MeterInfo meter : meters) {
                            try {
                                AnomalyDetector.MonthCalculationResult resultMonth =
                                    AnomalyDetector.analyzeMonth(conn, meter.dataId, statusTrendName, year, month);
                                
                                if (resultMonth.error != null) {
                                    metersFailed++;
                                    if (errors.size() < 5) {
                                        errors.add(meter.name + ": " + resultMonth.error);
                                    }
                                    continue;
                                }
                                if (resultMonth.statusError != null && errors.size() < 10) {
                                    errors.add(meter.name + " [статус]: " + resultMonth.statusError);
                                }
                                if (resultMonth.diagnostics != null && diag.size() < 3) {
                                    diag.add(meter.name + " | " + resultMonth.diagnostics);
                                }
                                totalConsumption += resultMonth.totalConsumption;
                                measuredConsumption += resultMonth.measuredConsumption;
                                calculatedConsumption += resultMonth.calculatedConsumption;
                                resetFilledConsumption += resultMonth.resetFilledConsumption;
                                if (resultMonth.isCalculated) {
                                    calculatedMeters++;
                                }
                                if (resultMonth.hasBoundaryGap) {
                                    boundaryGapMeters++;
                                }
                                if (!resultMonth.hasStatus) {
                                    noStatusMeters++;
                                }
                            } catch (Exception e) {
                                System.err.println("Error processing meter " + meter.name + " for " + year + "-" + month + ": " + e.getMessage());
                                // Продолжаем с другими счётчиками
                            }
                        }
                        
                        double calculatedPercent = 0;
                        if (totalConsumption > 0) {
                            calculatedPercent = (calculatedConsumption / totalConsumption) * 100;
                        }
                        
                        JSONObject monthData = new JSONObject();
                        monthData.put("year", year);
                        monthData.put("month", month);
                        monthData.put("totalConsumption", totalConsumption);
                        monthData.put("measuredConsumption", measuredConsumption);
                        monthData.put("calculatedConsumption", calculatedConsumption);
                        monthData.put("resetFilledConsumption", resetFilledConsumption);
                        monthData.put("calculatedPercent", Math.round(calculatedPercent * 100.0) / 100.0);
                        monthData.put("calculatedMeters", calculatedMeters);
                        monthData.put("metersWithBoundaryGap", boundaryGapMeters);
                        monthData.put("metersWithoutStatus", noStatusMeters);
                        monthData.put("metersFailed", metersFailed);
                        monthData.put("totalMeters", totalMeters);
                        
                        monthlyData.put(monthData);
                        
                        month++;
                        if (month > 12) {
                            month = 1;
                            year++;
                        }
                    }
                } else {
                    for (int i = 0; i < monthsCount; i++) {
                        JSONObject monthData = new JSONObject();
                        monthData.put("year", year);
                        monthData.put("month", month);
                        monthData.put("totalConsumption", 0);
                        monthData.put("measuredConsumption", 0);
                        monthData.put("calculatedConsumption", 0);
                        monthData.put("resetFilledConsumption", 0);
                        monthData.put("calculatedPercent", 0);
                        monthData.put("calculatedMeters", 0);
                        monthData.put("metersWithBoundaryGap", 0);
                        monthData.put("metersWithoutStatus", 0);
                        monthData.put("metersFailed", 0);
                        monthData.put("totalMeters", 0);
                        monthlyData.put(monthData);
                        
                        month++;
                        if (month > 12) {
                            month = 1;
                            year++;
                        }
                    }
                }
                
                groupData.put("data", monthlyData);
                groupData.put("errors", new JSONArray(errors));
                groupData.put("diag", new JSONArray(diag));
                result.put(groupData);
            }
            
            PrintWriter out = resp.getWriter();
            out.print(result.toString());
            
        } catch (Exception e) {
            e.printStackTrace();
            try {
                sendError(resp, e.getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    private List<MeterInfo> findMeters(SystemConnection conn, String path, String mask) throws Exception {
        final List<MeterInfo> result = new ArrayList<>();
        final String finalPath = path;
        
        conn.runReadAction(new ReadAction() {
            @Override
            public void execute(SystemAccess access) throws Exception {
                Tree geo = access.getTree(SystemTree.Geographic);
                Location rootLoc = geo.resolve(finalPath);
                scanLocation(rootLoc, mask, result);
            }
        });
        return result;
    }
    
    private void scanLocation(Location loc, String mask, List<MeterInfo> out) throws Exception {
        String name = loc.getReferenceName();
        
        if (name != null && name.contains(mask)) {
            
            // Исключаем сумматоры
            if (name.endsWith("_meter") || 
                name.endsWith("_total") || 
                name.endsWith("_sum") ||
                name.endsWith("_all")) {
                System.out.println("Skipping summarizer: " + name);
                for (Location child : loc.getChildren()) {
                    scanLocation(child, mask, out);
                }
                return;
            }
            
            try {
                // ====== ПРОВЕРЯЕМ НАЛИЧИЕ cons_total_net_tn ======
                Location netTrend = loc.getDescendant("cons_total_net_tn");
                if (netTrend != null && netTrend.hasAspect(TrendSource.class)) {
                    
                    MeterInfo info = new MeterInfo();
                    info.name = name;
                    String fullPath = loc.toString();
                    int hashIndex = fullPath.indexOf('#');
                    if (hashIndex != -1) {
                        info.path = fullPath.substring(hashIndex);
                    } else {
                        info.path = "#" + name;
                    }
                    
                    // ====== ДЛЯ ДАННЫХ: cons_total_net_tn ======
                    info.dataId = "ABSPATH:1:" + info.path + "/cons_total_net_tn";
                    
                    // ====== ДЛЯ СТАТУСА СВЯЗИ ======
                    // Тренд статуса здесь не вычисляется: анализатор находит его как соседнюю
                    // точку того же счётчика (getParent/getChild), без строковых идентификаторов.
                    
                    out.add(info);
                }
            } catch (Exception e) {
                // Нет тренда - пропускаем
            }
        }
        
        for (Location child : loc.getChildren()) {
            scanLocation(child, mask, out);
        }
    }
    
    private void sendError(HttpServletResponse resp, String message) throws Exception {
        resp.setContentType("application/json");
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        JSONObject error = new JSONObject();
        error.put("error", message);
        resp.getWriter().print(error.toString());
    }
    
    private static class MeterInfo {
        String name;
        String path;
        String dataId;    // cons_total_net_tn
    }
}