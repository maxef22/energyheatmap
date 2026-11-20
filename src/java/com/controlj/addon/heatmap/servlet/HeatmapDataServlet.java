package com.controlj.addon.heatmap.servlet;

import com.controlj.green.addonsupport.InvalidConnectionRequestException;
import com.controlj.green.addonsupport.access.*;
import com.controlj.green.addonsupport.access.aspect.AnalogTrendSource;
import com.controlj.green.addonsupport.access.aspect.DigitalTrendSource;
import com.controlj.green.addonsupport.access.aspect.TrendSource;
import com.controlj.green.addonsupport.access.trend.*;
import com.controlj.addon.heatmap.util.AnomalyDetector;
import com.controlj.addon.heatmap.util.SettingsManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@WebServlet("/data")
public class HeatmapDataServlet extends HttpServlet {
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public void init() throws ServletException {
        // Получаем путь к папке данных аддона из контекста (WebCTRL передаёт атрибут)
        String dataDir = (String) getServletContext().getAttribute("webapp.data.dir");
        if (dataDir == null) {
            // fallback – можно попробовать системное свойство
            dataDir = System.getProperty("webapp.data.dir");
        }
        if (dataDir != null) {
            SettingsManager.init(dataDir);
            System.out.println("[HeatmapDataServlet] SettingsManager initialized with dir: " + dataDir);
        } else {
            System.err.println("[HeatmapDataServlet] Could not determine webapp.data.dir, using defaults");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String id = req.getParameter("id");
        String startStr = req.getParameter("start");
        String endStr = req.getParameter("end");
        String aggregation = req.getParameter("aggregation");
        String mode = req.getParameter("mode");

        if (id == null || id.trim().isEmpty()) {
            sendError(resp, "Missing 'id' parameter");
            return;
        }
        if (startStr == null || startStr.trim().isEmpty()) {
            sendError(resp, "Missing 'start' parameter");
            return;
        }
        if (endStr == null || endStr.trim().isEmpty()) {
            sendError(resp, "Missing 'end' parameter");
            return;
        }

        try {
            Date start = dateFormatter.parse(startStr.trim());
            Date end = dateFormatter.parse(endStr.trim());
            end.setHours(24); // make end exclusive

            SystemConnection connection = DirectAccess.getDirectAccess().getUserSystemConnection(req);
            List<TrendSample> validSamples = fetchValidAnalogSamples(connection, id, start, end);

            if (validSamples.isEmpty()) {
                sendError(resp, "No valid analog trend data for the selected period");
                return;
            }

            resp.setContentType("application/json; charset=UTF-8");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter out = resp.getWriter();

            if ("hourly".equalsIgnoreCase(aggregation) && "consumption".equalsIgnoreCase(mode)) {
                List<HourlyConsumption> hourly = computeHourlyConsumption(validSamples);

                // Заполняем статистикой часы без связи, если включено
                if (SettingsManager.isAnomalyCorrectionEnabled()) {
                    int minDays = SettingsManager.getMinDaysForMedian();
                    AnomalyDetector.StatusData status = AnomalyDetector.readStatusForDataTrend(
                            connection, id, SettingsManager.getStatusTrendName(), start, end);
                    if (!status.found) {
                        System.out.println("[HeatmapDataServlet] статус связи недоступен для " + id
                                + ": " + status.error + " — заполняем только нулевые часы");
                    }
                    AnomalyDetector.correctOutliers(hourly, minDays, status.found ? status.byHour : null);
                }

                JSONObject result = new JSONObject();
                result.put("id", id);
                result.put("aggregation", "hourly");
                result.put("mode", "consumption");
                JSONArray dataArray = new JSONArray();
                for (HourlyConsumption hc : hourly) {
                    JSONObject item = new JSONObject();
                    item.put("ts", hc.timestamp);
                    item.put("value", hc.value);
                    dataArray.put(item);
                }
                result.put("data", dataArray);
                out.print(result.toString());
            } else {
                // raw numeric values
                JSONObject result = new JSONObject();
                result.put("id", id);
                JSONArray samplesArray = new JSONArray();
                for (TrendSample s : validSamples) {
                    JSONObject sampleJson = new JSONObject();
                    sampleJson.put("t", s.getTimeInMillis());
                    sampleJson.put("v", ((TrendAnalogSample) s).doubleValue());
                    samplesArray.put(sampleJson);
                }
                result.put("samples", samplesArray);
                out.print(result.toString());
            }

        } catch (ParseException e) {
            sendError(resp, "Invalid date format. Use yyyy-MM-dd");
        } catch (InvalidConnectionRequestException e) {
            sendError(resp, "Authentication failed");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(resp, e.getMessage());
        }
    }

    /**
     * Fetches analog trend samples that can safely provide a numeric value.
     * Skips samples that throw exception on doubleValue().
     */
    private List<TrendSample> fetchValidAnalogSamples(SystemConnection conn, String id, Date start, Date end) throws Exception {
        final List<TrendSample> result = new ArrayList<>();
        conn.runReadAction(new ReadAction() {
            @Override
            public void execute(SystemAccess access) throws Exception {
                Tree geo = access.getTree(SystemTree.Geographic);
                Location loc = geo.resolve(id);
                TrendSource ts = loc.getAspect(TrendSource.class);
                if (ts == null) {
                    System.out.println("[HeatmapDataServlet] тренд не найден: " + id);
                    return;
                }
                TrendRange range = TrendRangeFactory.byDateRange(start, end);
                TrendData<? extends TrendSample> data;
                if (ts.getType() == TrendSource.Type.Analog) {
                    data = ((AnalogTrendSource) ts).getTrendData(range);
                } else if (ts.getType() == TrendSource.Type.Digital) {
                    data = ((DigitalTrendSource) ts).getTrendData(range);
                } else {
                    return;
                }
                Iterator<? extends TrendSample> it = data.getSamples();
                while (it.hasNext()) {
                    TrendSample sample = it.next();
                    if (!(sample instanceof TrendAnalogSample)) continue;
                    if (sample.getTimeInMillis() >= end.getTime()) continue;
                    try {
                        ((TrendAnalogSample) sample).doubleValue();
                        result.add(sample);
                    } catch (Exception e) {
                        // bad sample (e.g., specialValue != 0) – skip
                    }
                }
            }
        });
        return result;
    }

    /**
     * Computes hourly consumption from cumulative values.
     */
    private List<HourlyConsumption> computeHourlyConsumption(List<TrendSample> samples) {
        Map<Long, Double> lastPerHour = new TreeMap<>();
        for (TrendSample s : samples) {
            double val = ((TrendAnalogSample) s).doubleValue();
            long hourStart = (s.getTimeInMillis() / 3600000) * 3600000;
            lastPerHour.put(hourStart, val);
        }
        List<HourlyConsumption> result = new ArrayList<>();
        Double previous = null;
        for (Map.Entry<Long, Double> entry : lastPerHour.entrySet()) {
            long hourStart = entry.getKey();
            double current = entry.getValue();
            if (previous != null) {
                double consumption = current - previous;
                if (consumption < 0) consumption = 0;
                result.add(new HourlyConsumption(hourStart, consumption));
            }
            previous = current;
        }
        return result;
    }

    private void sendError(HttpServletResponse resp, String message) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        JSONObject error = new JSONObject();
        error.put("error", message);
        resp.getWriter().print(error.toString());
    }

    // Вспомогательный класс для почасовых данных
    public static class HourlyConsumption {
        public long timestamp;
        public double value;
        public HourlyConsumption(long ts, double val) { timestamp = ts; value = val; }
    }
}