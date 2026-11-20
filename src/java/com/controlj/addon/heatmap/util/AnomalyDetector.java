package com.controlj.addon.heatmap.util;

import com.controlj.green.addonsupport.access.*;
import com.controlj.green.addonsupport.access.aspect.AnalogTrendSource;
import com.controlj.green.addonsupport.access.aspect.DigitalTrendSource;
import com.controlj.green.addonsupport.access.aspect.TrendSource;
import com.controlj.green.addonsupport.access.trend.*;
import com.controlj.addon.heatmap.servlet.HeatmapDataServlet;

import java.util.*;

/**
 * Расчёт объёма, который пришлось восстанавливать статистикой ("расчётные значения").
 *
 * МЕТОДИКА МЕСЯЧНОГО ОТЧЁТА:
 *  1. Данные — тренд cons_total_net_tn (накопительные показания счётчика).
 *     Статус связи — тренд com_st_min: 1 (или больше) — связи нет, 0 — связь есть.
 *     Все гипермаркеты работают в зоне Europe/Moscow.
 *  2. Месячная наработка = показание на конец месяца минус показание на начало месяца.
 *     Пропуски в СЕРЕДИНЕ месяца на разность не влияют (счётчик накопительный).
 *  3. Объём, потерянный на ГРАНИЦАХ месяца (ведущий и замыкающий блоки без связи),
 *     восстанавливается статистикой и идёт в числитель процента расчётных значений.
 *  4. Объём, потерянный из-за сброса/замены счётчика, тоже восстанавливается статистикой,
 *     но в числитель процента НЕ входит.
 *  5. Статистика — медиана по тому же часу суток за измеренные дни месяца
 *     (минимум значений задаётся настройкой minDaysForMedian).
 *
 * ВАЖНО: тренд статуса местами не читается целиком (SDK падает на отдельных записях),
 * поэтому статус читается ещё и по суткам от краёв месяца: сутки, которые не читаются,
 * считаются сутками без связи.
 */
public class AnomalyDetector {

    /** Все гипермаркеты работают в этой зоне. */
    public static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Moscow");

    private static final long HOUR_MS = 3600000L;
    private static final long DAY_MS = 24 * HOUR_MS;

    /** Сколько суток от края месяца максимум просматриваем при поштучном чтении статуса. */
    private static final int MAX_BOUNDARY_DAYS = 10;

    private static void debugLog(String message) {
        if (SettingsManager.isDebugLoggingEnabled()) {
            System.out.println("[AnomalyDetector] " + message);
        }
    }

    private static long hourBucket(long timestamp) {
        return (timestamp / HOUR_MS) * HOUR_MS;
    }

    /** Час суток по московскому времени. */
    private static int hourOfDay(long timestamp) {
        Calendar c = Calendar.getInstance(ZONE);
        c.setTimeInMillis(timestamp);
        return c.get(Calendar.HOUR_OF_DAY);
    }

    /** Начало суток (московское время) для момента времени. */
    private static long startOfDay(long timestamp) {
        Calendar c = Calendar.getInstance(ZONE);
        c.setTimeInMillis(timestamp);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ============================================================
    // ЗАПОЛНЕНИЕ СТАТИСТИКОЙ ДЛЯ ТЕПЛОВОЙ КАРТЫ
    // ============================================================

    /**
     * Заполняет статистикой часы, в которые не было связи, и часы с нулевым значением.
     *
     * @param statusByHour час -> статус (1 и больше — связи нет); null, если статус недоступен
     */
    public static void correctOutliers(List<HeatmapDataServlet.HourlyConsumption> data,
                                       int minValidDays,
                                       Map<Long, Double> statusByHour) {
        if (data == null || data.isEmpty()) return;

        Map<Integer, List<Double>> measuredByHourOfDay = new HashMap<>();
        for (HeatmapDataServlet.HourlyConsumption hc : data) {
            if (hc.value <= 0.0) continue;
            if (isCommunicationError(statusByHour, hc.timestamp)) continue;
            measuredByHourOfDay.computeIfAbsent(hourOfDay(hc.timestamp), k -> new ArrayList<>()).add(hc.value);
        }

        Map<Integer, Double> medianByHourOfDay = new HashMap<>();
        for (Map.Entry<Integer, List<Double>> entry : measuredByHourOfDay.entrySet()) {
            if (entry.getValue().size() >= minValidDays) {
                medianByHourOfDay.put(entry.getKey(), median(entry.getValue()));
            }
        }

        for (HeatmapDataServlet.HourlyConsumption hc : data) {
            boolean communicationError = isCommunicationError(statusByHour, hc.timestamp);
            if (!communicationError && hc.value != 0.0) continue;
            Double medianValue = medianByHourOfDay.get(hourOfDay(hc.timestamp));
            if (medianValue != null) {
                debugLog("заполнение " + new Date(hc.timestamp) + ": " + hc.value + " -> " + medianValue
                        + (communicationError ? " (нет связи)" : " (нулевое значение)"));
                hc.value = medianValue;
            }
        }
    }

    /** Значение статуса означает отсутствие связи; полярность задаётся настройкой statusErrorOnZero. */
    private static boolean isErrorStatus(Double status) {
        if (status == null) return false;
        return SettingsManager.isStatusErrorOnZero() ? status <= 0.0 : status >= 1.0;
    }

    private static boolean isCommunicationError(Map<Long, Double> statusByHour, long timestamp) {
        if (statusByHour == null) return false;
        return isErrorStatus(statusByHour.get(hourBucket(timestamp)));
    }

    // ============================================================
    // МЕСЯЧНЫЙ АНАЛИЗ ДЛЯ ОТЧЁТА ПО ГРУППЕ СЧЁТЧИКОВ
    // ============================================================

    public static MonthCalculationResult analyzeMonth(SystemConnection conn,
                                                      String dataId,
                                                      String statusTrendName,
                                                      int year,
                                                      int month) {
        try {
            return doAnalyzeMonth(conn, dataId, statusTrendName, year, month);
        } catch (Exception e) {
            System.err.println("[AnomalyDetector] ошибка расчёта " + year + "-" + month
                    + " для " + dataId + ": " + e);
            return MonthCalculationResult.failed(String.valueOf(e), false);
        }
    }

    private static MonthCalculationResult doAnalyzeMonth(SystemConnection conn,
                                                         String dataId,
                                                         String statusTrendName,
                                                         int year,
                                                         int month) throws Exception {
        Calendar cal = Calendar.getInstance(ZONE);
        cal.set(year, month - 1, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date monthStart = cal.getTime();
        cal.set(year, month - 1, cal.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59);
        cal.set(Calendar.MILLISECOND, 999);
        Date monthEnd = cal.getTime();
        Date rangeEnd = new Date(monthEnd.getTime() + 1);

        long monthStartHour = hourBucket(monthStart.getTime());
        long monthEndHour = hourBucket(monthEnd.getTime());

        debugLog("========================================");
        debugLog("analyzeMonth " + year + "-" + month + " dataId=" + dataId + " statusTrend=" + statusTrendName);

        // ---- 1. Показания счётчика по часам ----
        List<TrendSample> rawSamples = fetchTrendSamples(conn, dataId, monthStart, rangeEnd);
        TreeMap<Long, Double> lastPerHour = new TreeMap<>();
        Map<String, Integer> typeCounts = new TreeMap<>();

        for (TrendSample sample : rawSamples) {
            String typeName = String.valueOf(sample.getType());
            Integer seen = typeCounts.get(typeName);
            typeCounts.put(typeName, seen == null ? 1 : seen + 1);

            if (!(sample instanceof TrendAnalogSample)) continue;
            try {
                double value = ((TrendAnalogSample) sample).doubleValue();
                if (!Double.isNaN(value)) {
                    lastPerHour.put(hourBucket(sample.getTimeInMillis()), value);
                }
            } catch (Exception e) {
                // нечитаемая запись — пропускаем, час останется без показания
            }
        }

        if (lastPerHour.isEmpty()) {
            debugLog("числовых показаний нет -> 100% расчётных");
            return MonthCalculationResult.noData(false, "данные: " + typeCounts, null);
        }

        // ---- 2. Статус связи: сначала пробуем прочитать месяц целиком ----
        StatusData monthStatus = readStatusForDataTrend(conn, dataId, statusTrendName, monthStart, rangeEnd);
        Map<Long, Double> statusByHour = new TreeMap<>();
        String statusSource;
        StringBuilder dayLog = new StringBuilder();

        if (monthStatus.found && !monthStatus.byHour.isEmpty()) {
            statusByHour.putAll(monthStatus.byHour);
            statusSource = "тренд " + statusTrendName + " за месяц (" + monthStatus.byHour.size() + " точек)";
        } else {
            statusSource = "тренд " + statusTrendName + " по суткам (месяц целиком не читается)";
            readBoundaryStatus(conn, dataId, statusTrendName, monthStartHour, monthEndHour,
                    MAX_BOUNDARY_DAYS, true, statusByHour, dayLog);
            readBoundaryStatus(conn, dataId, statusTrendName, monthStartHour, monthEndHour,
                    MAX_BOUNDARY_DAYS, false, statusByHour, dayLog);
        }

        // Тренд статуса годится, только если он реально различает "связь есть" и "связи нет".
        // Иначе (на объектах он бывает всегда равен 1) признаком обрыва считаем
        // замерший счётчик: накопительное значение не прибавляется.
        int statusOk = 0;
        int statusErr = 0;
        for (Double value : statusByHour.values()) {
            if (isErrorStatus(value)) statusErr++;
            else statusOk++;
        }
        boolean statusDiscriminates = statusOk > 0 && statusErr > 0;

        int frozenRunHours = SettingsManager.getFrozenRunHours();
        // Значение, которым помечаем часы как "связи нет" с учётом выбранной полярности
        double errorMark = SettingsManager.isStatusErrorOnZero() ? 0.0 : 1.0;
        long frozenLeadingEnd = -1;
        long frozenTrailingStart = -1;
        if (!statusDiscriminates) {
            statusByHour.clear();
            statusSource += " — не различает состояния";
            frozenLeadingEnd = leadingFrozenEnd(lastPerHour, monthStartHour, monthEndHour, frozenRunHours);
            frozenTrailingStart = trailingFrozenStart(lastPerHour, monthStartHour, monthEndHour, frozenRunHours);
            for (long h = monthStartHour; h < frozenLeadingEnd; h += HOUR_MS) {
                statusByHour.put(h, errorMark);
            }
            for (long h = frozenTrailingStart + HOUR_MS; h <= monthEndHour; h += HOUR_MS) {
                statusByHour.put(h, errorMark);
            }
            statusSource += ", признак — замерший счётчик (порог " + frozenRunHours + " ч)";
        }
        boolean hasStatus = !statusByHour.isEmpty();

        String diagnostics = "данные: " + typeCounts + " | статус: " + statusSource
                + (statusOk + statusErr > 0 ? " (0=" + statusErr + ", 1=" + statusOk + ")" : "")
                + (monthStatus.info == null ? "" : " | тренд: " + monthStatus.info)
                + (monthStatus.error == null ? "" : " | ошибка чтения месяца: " + monthStatus.error)
                + " | 1-е сутки: " + zeroDeltaSummary(lastPerHour, startOfDay(monthStartHour), startOfDay(monthStartHour) + DAY_MS - 1)
                + " | последние сутки: " + zeroDeltaSummary(lastPerHour, startOfDay(monthEndHour), monthEndHour)
                + (frozenLeadingEnd >= 0 ? " | заморозка в начале: " + ((frozenLeadingEnd - monthStartHour) / HOUR_MS) + " ч" : "")
                + (frozenTrailingStart >= 0 ? " | заморозка в конце: " + ((monthEndHour - frozenTrailingStart) / HOUR_MS) + " ч" : "")
                + (dayLog.length() == 0 ? "" : " | сутки: " + dayLog);

        // ---- 3. Границы месяца: идём от краёв, пока час не станет пригодным ----
        // Пригодный час = есть показание счётчика и связь не в ошибке.
        long firstGoodHour = monthStartHour;
        while (firstGoodHour <= monthEndHour && !isGoodHour(firstGoodHour, lastPerHour, statusByHour)) {
            firstGoodHour += HOUR_MS;
        }
        long lastGoodHour = monthEndHour;
        while (lastGoodHour >= firstGoodHour && !isGoodHour(lastGoodHour, lastPerHour, statusByHour)) {
            lastGoodHour -= HOUR_MS;
        }

        if (firstGoodHour > lastGoodHour) {
            debugLog("нет зоны с нормальной связью -> 100% расчётных");
            return MonthCalculationResult.noData(hasStatus, diagnostics, monthStatus.error);
        }

        // ---- 4. Измеренный объём внутри зоны ----
        double measuredVolume = 0;
        long measuredHours = 0;
        Map<Integer, List<Double>> measuredByHourOfDay = new HashMap<>();
        List<Long> resetHours = new ArrayList<>();

        Long prevHour = null;
        Double prevValue = null;
        for (Map.Entry<Long, Double> entry : lastPerHour.entrySet()) {
            long hour = entry.getKey();
            double value = entry.getValue();
            if (hour < firstGoodHour || hour > lastGoodHour) {
                prevHour = null;
                prevValue = null;
                continue;
            }
            if (prevValue != null) {
                double delta = value - prevValue;
                long spanHours = (hour - prevHour) / HOUR_MS;
                if (delta >= 0) {
                    measuredVolume += delta;
                    measuredHours += spanHours;
                    if (spanHours == 1) {
                        measuredByHourOfDay.computeIfAbsent(hourOfDay(hour), k -> new ArrayList<>()).add(delta);
                    }
                } else {
                    for (long h = prevHour + HOUR_MS; h <= hour; h += HOUR_MS) {
                        resetHours.add(h);
                    }
                }
            }
            prevHour = hour;
            prevValue = value;
        }

        if (measuredHours == 0) {
            debugLog("измеренного объёма нет -> 100% расчётных");
            return MonthCalculationResult.noData(hasStatus, diagnostics, monthStatus.error);
        }

        Map<Integer, Double> medianByHourOfDay = new HashMap<>();
        int minDaysForMedian = SettingsManager.getMinDaysForMedian();
        for (Map.Entry<Integer, List<Double>> entry : measuredByHourOfDay.entrySet()) {
            if (entry.getValue().size() >= minDaysForMedian) {
                medianByHourOfDay.put(entry.getKey(), median(entry.getValue()));
            }
        }
        double fallbackHourVolume = measuredHours > 0 ? measuredVolume / measuredHours : 0.0;

        // ---- 5. Граничные блоки ----
        double startFilled = 0;
        int startHours = 0;
        for (long h = monthStartHour; h < firstGoodHour; h += HOUR_MS) {
            startFilled += estimateHour(h, medianByHourOfDay, fallbackHourVolume);
            startHours++;
        }
        double endFilled = 0;
        int endHours = 0;
        for (long h = lastGoodHour + HOUR_MS; h <= monthEndHour; h += HOUR_MS) {
            endFilled += estimateHour(h, medianByHourOfDay, fallbackHourVolume);
            endHours++;
        }

        // ---- 6. Восстановление после сбросов (в числитель не входит) ----
        double resetFilledVolume = 0;
        for (Long hour : resetHours) {
            resetFilledVolume += estimateHour(hour, medianByHourOfDay, fallbackHourVolume);
        }

        double calculatedVolume = startFilled + endFilled;
        int calculatedHours = startHours + endHours;
        double totalVolume = measuredVolume + calculatedVolume + resetFilledVolume;
        double calculatedPercent = totalVolume > 0 ? (calculatedVolume / totalVolume) * 100.0 : 0.0;

        double firstValue = lastPerHour.get(firstGoodHour) - startFilled;
        double lastValue = lastPerHour.get(lastGoodHour) + endFilled;

        System.out.println("[AnomalyDetector] " + year + "-" + month + " " + dataId
                + ": измерено " + measuredVolume + ", расчётное " + calculatedVolume
                + " (" + startHours + " ч в начале, " + endHours + " ч в конце)"
                + ", сбросы " + resetFilledVolume + ", наработка " + totalVolume
                + ", " + calculatedPercent + "% | " + diagnostics);

        return new MonthCalculationResult(
                calculatedVolume > 0,
                Math.round(calculatedPercent * 100.0) / 100.0,
                totalVolume,
                calculatedVolume,
                calculatedHours,
                (int) ((monthEndHour - monthStartHour) / HOUR_MS + 1),
                firstValue,
                lastValue,
                startHours > 0,
                endHours > 0,
                measuredVolume,
                resetFilledVolume,
                (startHours > 0 || endHours > 0),
                hasStatus,
                null,
                monthStatus.error,
                diagnostics);
    }

    /**
     * Читает статус по суткам от начала (fromStart=true) или от конца месяца.
     * Сутки, которые не читаются, считаются сутками без связи.
     *
     * @return час, с которого (для fromStart) связь в норме; для fromEnd — последний час со связью
     */
    private static long readBoundaryStatus(SystemConnection conn,
                                           String dataId,
                                           String statusTrendName,
                                           long monthStartHour,
                                           long monthEndHour,
                                           int maxDays,
                                           boolean fromStart,
                                           Map<Long, Double> statusOut,
                                           StringBuilder dayLog) {
        for (int i = 0; i < maxDays; i++) {
            long dayStart = startOfDay(fromStart
                    ? monthStartHour + (long) i * DAY_MS
                    : monthEndHour - (long) i * DAY_MS);
            long dayEnd = dayStart + DAY_MS - 1;
            if (dayStart > monthEndHour || dayEnd < monthStartHour) break;

            Date from = new Date(Math.max(dayStart, monthStartHour));
            Date to = new Date(Math.min(dayEnd, monthEndHour));
            StatusData day = readStatusForDataTrend(conn, dataId, statusTrendName, from, to);

            String dayLabel = String.format(Locale.ROOT, "%1$td.%1$tm", new Date(dayStart));
            if (!day.found || day.byHour.isEmpty()) {
                // сутки не читаются — считаем, что связи не было
                double errorMark = SettingsManager.isStatusErrorOnZero() ? 0.0 : 1.0;
                for (long h = Math.max(dayStart, monthStartHour); h <= Math.min(dayEnd, monthEndHour); h += HOUR_MS) {
                    statusOut.put(h, errorMark);
                }
                dayLog.append(dayLabel).append("=нет данных; ");
                continue;
            }

            statusOut.putAll(day.byHour);
            dayLog.append(dayLabel).append("=ok(").append(day.byHour.size()).append(" ")
                    .append(histogram(day.byHour)).append("); ");

            // ищем первый (или последний) час со связью внутри суток
            long edge = -1;
            long from2 = Math.max(dayStart, monthStartHour);
            long to2 = Math.min(dayEnd, monthEndHour);
            if (fromStart) {
                for (long h = from2; h <= to2; h += HOUR_MS) {
                    Double st = day.byHour.get(h);
                    if (st != null && !isErrorStatus(st)) {
                        edge = h;
                        break;
                    }
                }
            } else {
                for (long h = to2; h >= from2; h -= HOUR_MS) {
                    Double st = day.byHour.get(h);
                    if (st != null && !isErrorStatus(st)) {
                        edge = h;
                        break;
                    }
                }
            }
            if (edge >= 0) {
                return edge;
            }
        }

        // связь не нашлась в пределах окна — блок тянется до конца окна
        return fromStart
                ? Math.min(monthEndHour + HOUR_MS, monthStartHour + (long) maxDays * DAY_MS)
                : Math.max(monthStartHour - HOUR_MS, monthEndHour - (long) maxDays * DAY_MS);
    }

    /** Час пригоден: есть показание и связь не в ошибке. */
    private static boolean isGoodHour(long hour, Map<Long, Double> readings, Map<Long, Double> statusByHour) {
        if (!readings.containsKey(hour)) return false;
        if (statusByHour != null && isErrorStatus(statusByHour.get(hour))) return false;
        return true;
    }

    /** Распределение значений статуса за сутки — для диагностики. */
    private static String histogram(Map<Long, Double> values) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Double v : values.values()) {
            String key = String.valueOf(v);
            Integer seen = counts.get(key);
            counts.put(key, seen == null ? 1 : seen + 1);
        }
        return counts.toString();
    }

    /** Сколько часов в сутках без прироста показаний — для диагностики. */
    private static String zeroDeltaSummary(TreeMap<Long, Double> readings, long from, long to) {
        int total = 0;
        int zeros = 0;
        int resets = 0;
        Long prevHour = null;
        Double prevValue = null;
        for (Map.Entry<Long, Double> entry : readings.entrySet()) {
            if (entry.getKey() < from || entry.getKey() > to) continue;
            if (prevValue != null) {
                double delta = entry.getValue() - prevValue;
                total++;
                if (delta == 0.0) zeros++;
                if (delta < 0.0) resets++;
            }
            prevHour = entry.getKey();
            prevValue = entry.getValue();
        }
        return zeros + "/" + total + " ч с нулевым приростом" + (resets > 0 ? ", сбросов " + resets : "");
    }

    /**
     * Конец ведущего блока «замершего» счётчика: идём от начала месяца, пока показания
     * не прибавляются. Если блок короче порога, считаем, что обрыва нет
     * (короткие ночные нули не должны попадать в расчётные).
     */
    private static long leadingFrozenEnd(TreeMap<Long, Double> readings,
                                         long monthStartHour,
                                         long monthEndHour,
                                         int runHours) {
        Long prevHour = null;
        Double prevValue = null;
        long runEnd = -1;
        for (Map.Entry<Long, Double> entry : readings.entrySet()) {
            long hour = entry.getKey();
            if (hour < monthStartHour || hour > monthEndHour) continue;
            if (prevValue != null && entry.getValue() - prevValue > 0.0) {
                runEnd = hour;
                break;
            }
            prevHour = hour;
            prevValue = entry.getValue();
        }
        if (runEnd < 0) runEnd = monthEndHour + HOUR_MS;
        long lengthHours = (runEnd - monthStartHour) / HOUR_MS;
        return lengthHours >= runHours ? runEnd : monthStartHour;
    }

    /** Начало замыкающего блока «замершего» счётчика (считаем с конца месяца). */
    private static long trailingFrozenStart(TreeMap<Long, Double> readings,
                                            long monthStartHour,
                                            long monthEndHour,
                                            int runHours) {
        List<Map.Entry<Long, Double>> list = new ArrayList<>(readings.entrySet());
        Collections.reverse(list);
        Long nextHour = null;
        Double nextValue = null;
        long runStart = -1;
        for (Map.Entry<Long, Double> entry : list) {
            long hour = entry.getKey();
            if (hour < monthStartHour || hour > monthEndHour) continue;
            if (nextValue != null && nextValue - entry.getValue() > 0.0) {
                runStart = hour;
                break;
            }
            nextHour = hour;
            nextValue = entry.getValue();
        }
        if (runStart < 0) runStart = monthStartHour - HOUR_MS;
        long lengthHours = (monthEndHour - runStart) / HOUR_MS;
        return lengthHours >= runHours ? runStart : monthEndHour;
    }

    private static double estimateHour(long hour, Map<Integer, Double> medianByHourOfDay, double fallback) {
        Double medianValue = medianByHourOfDay.get(hourOfDay(hour));
        return medianValue != null ? medianValue : fallback;
    }

    // ============================================================
    // ЧТЕНИЕ ТРЕНДА СТАТУСА
    // ============================================================

    /** Данные тренда статуса связи за период. */
    public static class StatusData {
        /** час -> com_st_min (1 и больше — связи нет, 0 — связь есть). */
        public final Map<Long, Double> byHour = new TreeMap<>();
        /** Прочитано хотя бы одно значение. */
        public boolean found;
        /** Причина, если прочитать не удалось. */
        public String error;
        /** Параметры тренда (диагностика). */
        public String info;
    }

    /**
     * Читает тренд статуса связи того же счётчика, которому принадлежит тренд потребления:
     * resolve тренда потребления -> getParent() -> поиск точки статуса среди потомков.
     * Строковые id не используются: путь к тренду статуса через geo.resolve() не резолвится.
     */
    public static StatusData readStatusForDataTrend(SystemConnection conn,
                                                    String dataTrendId,
                                                    String statusTrendName,
                                                    Date start,
                                                    Date end) {
        final StatusData out = new StatusData();
        if (statusTrendName == null || statusTrendName.trim().isEmpty()) {
            out.error = "имя тренда статуса не задано";
            return out;
        }
        final String trendName = statusTrendName.trim();
        try {
            conn.runReadAction(new ReadAction() {
                @Override
                public void execute(SystemAccess access) throws Exception {
                    Tree geo = access.getTree(SystemTree.Geographic);
                    Location dataPoint = geo.resolve(dataTrendId);
                    Location meter = dataPoint.getParent();

                    Location statusPoint = null;
                    try {
                        statusPoint = meter.getDescendant(trendName);
                    } catch (UnresolvableException e) {
                        // попробуем прямой поиск ниже
                    }
                    if (statusPoint == null) {
                        try {
                            statusPoint = meter.getChild(trendName);
                        } catch (UnresolvableException e) {
                            out.error = "не найдена точка " + trendName + "; дети: " + childNames(meter);
                            return;
                        }
                    }
                    if (!statusPoint.hasAspect(TrendSource.class)) {
                        out.error = "у точки " + trendName + " нет аспекта TrendSource; дети: " + childNames(meter);
                        return;
                    }

                    TrendSource ts = statusPoint.getAspect(TrendSource.class);
                    if (out.info == null) {
                        StringBuilder info = new StringBuilder();
                        info.append("type=").append(ts.getType()).append(", enabled=").append(ts.isEnabled());
                        try {
                            info.append(", samples=").append(ts.getNumberOfSamplesInDatabase());
                        } catch (Exception e) {
                            info.append(", meta: ").append(e);
                        }
                        out.info = info.toString();
                    }

                    TrendData<? extends TrendSample> data;
                    if (ts.getType() == TrendSource.Type.Analog) {
                        data = ((AnalogTrendSource) ts).getTrendData(TrendRangeFactory.byDateRange(start, end));
                    } else if (ts.getType() == TrendSource.Type.Digital) {
                        data = ((DigitalTrendSource) ts).getTrendData(TrendRangeFactory.byDateRange(start, end));
                    } else {
                        out.error = "неподдерживаемый тип тренда статуса: " + ts.getType();
                        return;
                    }

                    Iterator<? extends TrendSample> it = data.getSamples();
                    int failures = 0;
                    String lastFailure = null;
                    while (true) {
                        TrendSample sample;
                        try {
                            if (!it.hasNext()) break;
                            sample = it.next();
                        } catch (Exception e) {
                            lastFailure = String.valueOf(e);
                            if (++failures > 5) break;
                            continue;
                        }
                        long hour = hourBucket(sample.getTimeInMillis());
                        if (sample instanceof TrendDigitalSample) {
                            out.byHour.put(hour, ((TrendDigitalSample) sample).getState() ? 1.0 : 0.0);
                        } else if (sample instanceof TrendAnalogSample) {
                            try {
                                double value = ((TrendAnalogSample) sample).doubleValue();
                                if (!Double.isNaN(value)) {
                                    out.byHour.put(hour, value);
                                }
                            } catch (Exception e) {
                                lastFailure = String.valueOf(e);
                            }
                        }
                    }
                    out.found = !out.byHour.isEmpty();
                    if (!out.found) {
                        out.error = lastFailure != null ? lastFailure : "в тренде статуса нет значений";
                    }
                }
            });
        } catch (Exception e) {
            out.error = String.valueOf(e);
        }
        return out;
    }

    /** Имена дочерних узлов — для диагностики. */
    private static String childNames(Location loc) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Location child : loc.getChildren()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(child.getReferenceName());
                if (child.hasAspect(TrendSource.class)) sb.append("(trend)");
                if (sb.length() > 300) {
                    sb.append("...");
                    break;
                }
            }
        } catch (Exception e) {
            sb.append("ошибка перечисления: ").append(e);
        }
        return sb.toString();
    }

    /** Читает все выборки накопительного тренда потребления. */
    private static List<TrendSample> fetchTrendSamples(SystemConnection conn,
                                                       String id,
                                                       Date start,
                                                       Date end) throws Exception {
        final List<TrendSample> result = new ArrayList<>();

        conn.runReadAction(new ReadAction() {
            @Override
            public void execute(SystemAccess access) throws Exception {
                Tree geo = access.getTree(SystemTree.Geographic);
                Location loc = geo.resolve(id);
                TrendSource ts = loc.getAspect(TrendSource.class);
                if (ts == null) {
                    debugLog("тренд потребления не найден: " + id);
                    return;
                }
                if (ts.getType() != TrendSource.Type.Analog) {
                    debugLog("тренд потребления не числовой: " + id + " (" + ts.getType() + ")");
                    return;
                }
                TrendData<? extends TrendSample> data =
                        ((AnalogTrendSource) ts).getTrendData(TrendRangeFactory.byDateRange(start, end));

                Iterator<? extends TrendSample> it = data.getSamples();
                int failures = 0;
                while (true) {
                    try {
                        if (!it.hasNext()) break;
                        result.add(it.next());
                    } catch (Exception e) {
                        if (++failures > 20) break;
                    }
                }
            }
        });

        return result;
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size == 0) return 0.0;
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        }
        return sorted.get(size / 2);
    }

    // ============================================================
    // РЕЗУЛЬТАТ РАСЧЁТА МЕСЯЦА
    // ============================================================

    public static class MonthCalculationResult {
        public final boolean isCalculated;
        public final double calculatedPercent;
        /** Наработка: измеренный объём + расчётный + восстановленный из-за сбросов. */
        public final double totalConsumption;
        /** Расчётный объём по границам месяца (числитель процента). */
        public final double calculatedConsumption;
        public final int calculatedHours;
        public final int totalHours;
        public final double firstValue;
        public final double lastValue;
        public final boolean firstIsCalculated;
        public final boolean lastIsCalculated;
        /** Измеренный объём (реальные приросты показаний). */
        public final double measuredConsumption;
        /** Объём, восстановленный из-за сброса/замены счётчика. */
        public final double resetFilledConsumption;
        public final boolean hasBoundaryGap;
        public final boolean hasStatus;
        /** Ошибка расчёта месяца, если была. */
        public final String error;
        /** Ошибка чтения тренда статуса, если была. */
        public final String statusError;
        /** Диагностика: типы записей тренда и источник статуса. */
        public final String diagnostics;

        public MonthCalculationResult(boolean isCalculated,
                                      double calculatedPercent,
                                      double totalConsumption,
                                      double calculatedConsumption,
                                      int calculatedHours,
                                      int totalHours,
                                      double firstValue,
                                      double lastValue,
                                      boolean firstIsCalculated,
                                      boolean lastIsCalculated,
                                      double measuredConsumption,
                                      double resetFilledConsumption,
                                      boolean hasBoundaryGap,
                                      boolean hasStatus,
                                      String error,
                                      String statusError,
                                      String diagnostics) {
            this.isCalculated = isCalculated;
            this.calculatedPercent = calculatedPercent;
            this.totalConsumption = totalConsumption;
            this.calculatedConsumption = calculatedConsumption;
            this.calculatedHours = calculatedHours;
            this.totalHours = totalHours;
            this.firstValue = firstValue;
            this.lastValue = lastValue;
            this.firstIsCalculated = firstIsCalculated;
            this.lastIsCalculated = lastIsCalculated;
            this.measuredConsumption = measuredConsumption;
            this.resetFilledConsumption = resetFilledConsumption;
            this.hasBoundaryGap = hasBoundaryGap;
            this.hasStatus = hasStatus;
            this.error = error;
            this.statusError = statusError;
            this.diagnostics = diagnostics;
        }

        public static MonthCalculationResult noData(boolean hasStatus, String diagnostics, String statusError) {
            return new MonthCalculationResult(true, 100.0, 0.0, 0.0, 0, 0,
                    0.0, 0.0, true, true, 0.0, 0.0, true, hasStatus, null, statusError, diagnostics);
        }

        public static MonthCalculationResult failed(String message, boolean hasStatus) {
            return new MonthCalculationResult(false, 0.0, 0.0, 0.0, 0, 0,
                    0.0, 0.0, false, false, 0.0, 0.0, false, hasStatus, message, null, null);
        }
    }
}
