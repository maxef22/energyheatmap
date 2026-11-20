package com.controlj.addon.heatmap.servlet;

import com.controlj.green.addonsupport.access.*;
import com.controlj.green.addonsupport.access.aspect.AnalogTrendSource;
import com.controlj.green.addonsupport.access.aspect.TrendSource;
import com.controlj.green.addonsupport.access.trend.*;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

@WebServlet("/data/aggregate")
public class AggregateDataServlet extends HttpServlet {
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        resp.setContentType("application/json; charset=UTF-8");
        try {
            String idsParam = req.getParameter("ids");
            String startStr = req.getParameter("start");
            String endStr = req.getParameter("end");

            if (idsParam == null || idsParam.trim().isEmpty()) {
                sendError(resp, "Missing 'ids' parameter");
                return;
            }
            if (startStr == null || endStr == null) {
                sendError(resp, "Missing start/end dates");
                return;
            }

            String[] ids = idsParam.split(",");
            Date start = dateFormatter.parse(startStr.trim());
            Date end = dateFormatter.parse(endStr.trim());
            end.setHours(24);

            SystemConnection conn = DirectAccess.getDirectAccess().getUserSystemConnection(req);
            
            // Хранилище для агрегированных данных: час -> сумма
            Map<Long, Double> aggregatedData = new HashMap<>();
            // Сколько счётчиков дали данные за каждый час (для усреднения, если нужно)
            Map<Long, Integer> hourCount = new HashMap<>();

            for (String id : ids) {
                List<TrendSample> samples = fetchValidAnalogSamples(conn, id.trim(), start, end);
                Map<Long, Double> hourlyConsumption = computeHourlyConsumption(samples);
                
                for (Map.Entry<Long, Double> entry : hourlyConsumption.entrySet()) {
                    long hourStart = entry.getKey();
                    double consumption = entry.getValue();
                    
                    // Пропускаем null или отрицательные значения
                    if (consumption < 0 || Double.isNaN(consumption)) {
                        continue;
                    }
                    
                    aggregatedData.put(hourStart, aggregatedData.getOrDefault(hourStart, 0.0) + consumption);
                    hourCount.put(hourStart, hourCount.getOrDefault(hourStart, 0) + 1);
                }
            }

            // Сортируем ключи
            JSONObject result = new JSONObject();
            result.put("aggregated", true);
            result.put("count", ids.length);
            JSONArray dataArray = new JSONArray();
            
            List<Long> sortedTimestamps = new ArrayList<>(aggregatedData.keySet());
            Collections.sort(sortedTimestamps);

            for (long ts : sortedTimestamps) {
                JSONObject item = new JSONObject();
                item.put("ts", ts);
                item.put("value", aggregatedData.get(ts));
                dataArray.put(item);
            }
            result.put("data", dataArray);

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
    
    private List<TrendSample> fetchValidAnalogSamples(SystemConnection conn, String id, Date start, Date end) throws Exception {
        final List<TrendSample> result = new ArrayList<>();
        conn.runReadAction(new ReadAction() {
            @Override
            public void execute(SystemAccess access) throws Exception {
                Tree geo = access.getTree(SystemTree.Geographic);
                Location loc = geo.resolve(id);
                TrendSource ts = loc.getAspect(TrendSource.class);
                TrendRange range = TrendRangeFactory.byDateRange(start, end);
                TrendData<? extends TrendSample> data;
                if (ts.getType() == TrendSource.Type.Analog) {
                    data = ((AnalogTrendSource) ts).getTrendData(range);
                } else {
                    return;
                }
                Iterator<? extends TrendSample> it = data.getSamples();
                while (it.hasNext()) {
                    TrendSample sample = it.next();
                    if (sample instanceof TrendAnalogSample) {
                        try {
                            double val = ((TrendAnalogSample) sample).doubleValue();
                            // Проверяем, что значение валидное (не отрицательное и не NaN)
                            if (!Double.isNaN(val) && val >= 0) {
                                result.add(sample);
                            } else {
                                System.out.println("Skipping invalid sample for " + id + ": value=" + val);
                            }
                        } catch (Exception e) {
                            // Пропускаем ошибочные сэмплы
                            System.out.println("Error reading sample for " + id + ": " + e.getMessage());
                        }
                    }
                }
            }
        });
        return result;
    }
    
    private Map<Long, Double> computeHourlyConsumption(List<TrendSample> samples) {
        Map<Long, Double> lastPerHour = new TreeMap<>();
        for (TrendSample s : samples) {
            double val = ((TrendAnalogSample) s).doubleValue();
            long hourStart = (s.getTimeInMillis() / 3600000) * 3600000;
            lastPerHour.put(hourStart, val);
        }
        Map<Long, Double> result = new TreeMap<>();
        Double previous = null;
        for (Map.Entry<Long, Double> entry : lastPerHour.entrySet()) {
            long hourStart = entry.getKey();
            double current = entry.getValue();
            if (previous != null && current >= previous) {
                double consumption = current - previous;
                if (consumption < 0) consumption = 0;
                result.put(hourStart, consumption);
            } else if (previous != null && current < previous) {
                // Сброс счётчика - не учитываем consumption
                System.out.println("Counter reset detected at hour " + hourStart + ", skipping");
            }
            previous = current;
        }
        return result;
    }
    
    private void sendError(HttpServletResponse resp, String message) throws Exception {
        resp.setContentType("application/json");
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        JSONObject error = new JSONObject();
        error.put("error", message);
        resp.getWriter().print(error.toString());
    }
}