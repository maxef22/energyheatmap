<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
<head>
    <title>Energy Heatmap & Report</title>
    <meta charset="UTF-8">
    <style>
        body { background: #1a1a1a; color: #f0f0f0; font-family: Arial, sans-serif; margin: 20px; }
        h1 { color: #ffd700; text-align: center; }
        .controls {
            background: #2a2a2a;
            padding: 15px;
            border-radius: 8px;
            margin-bottom: 20px;
            display: flex;
            gap: 15px;
            flex-wrap: wrap;
            align-items: center;
            justify-content: center;
        }
        .controls label { color: #ffd700; }
        .controls input, .controls select, .controls button {
            padding: 6px 12px;
            border-radius: 4px;
            border: none;
            background: #3a3a3a;
            color: white;
        }
        .controls button {
            background: #ffd700;
            color: #1a1a1a;
            font-weight: bold;
            cursor: pointer;
        }
        .controls button:hover {
            background: #e6c200;
        }
        .toggle-tree-btn {
            background: #ffd700;
            color: #1a1a1a;
            border: none;
            border-radius: 4px;
            padding: 6px 12px;
            cursor: pointer;
            font-weight: bold;
        }
        table {
            border-collapse: collapse;
            margin: 20px auto;
            background: #0d0d0d;
        }
        th, td {
            border: 1px solid #333;
            padding: 8px 10px;
            text-align: center;
            font-size: 12px;
        }
        th { background: #2a2a2a; color: #ffd700; }
        .hour-col, .week-col { background: #2a2a2a; font-weight: bold; }
        .legend {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
            margin: 20px auto 5px auto;
            width: 300px;
            background: #2a2a2a;
            padding: 8px 15px;
            border-radius: 20px;
        }
        .legend-colors { flex: 1; height: 20px; background: linear-gradient(to right, green, yellow, red); border-radius: 4px; }
        .legend-values {
            display: flex;
            justify-content: space-between;
            width: 300px;
            margin: 0 auto 10px auto;
            font-size: 10px;
            color: #ccc;
        }
        .total-summary {
            text-align: center;
            margin: 15px auto;
            padding: 10px;
            background: #2a2a2a;
            border-radius: 8px;
            display: inline-block;
            width: auto;
            min-width: 250px;
        }
        .total-summary .total-value {
            font-size: 18px;
            color: #ffd700;
            font-weight: bold;
        }
        .footer { text-align: center; margin-top: 20px; font-size: 12px; color: #888; }
        .error { color: red; text-align: center; margin: 10px; }
        
        /* Tree styles */
        .tree { list-style: none; padding-left: 0; margin: 0; }
        .tree ul { list-style: none; padding-left: 20px; }
        .tree li { margin: 4px 0; }
        .tree .node {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 2px 6px;
            border-radius: 4px;
            cursor: pointer;
        }
        .tree .node:hover { background-color: #3a3a3a; }
        .tree .folder-icon::before { content: "📁 "; }
        .tree .leaf-icon::before { content: "📄 "; }
        .tree .selected { background-color: #ffd700; color: #1a1a1a; }
        .tree-checkbox {
            margin-right: 5px;
            cursor: pointer;
            width: 14px;
            height: 14px;
        }
        .tree-container {
            max-height: 350px;
            overflow-y: auto;
            background: #2a2a2a;
            padding: 8px;
            border-radius: 8px;
            margin: 0 0 20px 0;
            width: 100%;
        }
        .selection-bar {
            background: #2a2a2a;
            padding: 8px 15px;
            border-radius: 8px;
            margin-bottom: 20px;
            display: flex;
            gap: 15px;
            flex-wrap: wrap;
            align-items: center;
            justify-content: space-between;
        }
        .selection-info {
            color: #ffd700;
            font-size: 12px;
        }
        .aggregate-btn {
            background: #ff4444;
            color: white;
            border: none;
            border-radius: 4px;
            padding: 6px 12px;
            cursor: pointer;
            font-weight: bold;
        }
        .aggregate-btn:hover {
            background: #cc0000;
        }
        .mode-info {
            text-align: center;
            font-size: 11px;
            color: #aaa;
            margin: 5px auto;
        }
        .view-selector {
            background: #ffd700;
            color: #1a1a1a;
            font-weight: bold;
        }
        
        /* Report styles */
        .report-table th {
            background: #2a2a2a;
            color: #ffd700;
        }
        .calculated-high {
            background-color: #8b0000;
            color: white;
            font-weight: bold;
        }
        .calculated-medium {
            background-color: #b8860b;
            color: white;
        }
        .calculated-low {
            background-color: #006400;
            color: white;
        }
        .calculated-none {
            background-color: #2a2a2a;
            color: #aaa;
        }
        .export-btn {
            background: #2196F3;
            color: white;
            border: none;
            border-radius: 4px;
            padding: 6px 12px;
            cursor: pointer;
            font-weight: bold;
        }
        .export-btn:hover {
            background: #1976D2;
        }
        .summary-block {
            background: #2a2a2a;
            padding: 15px;
            border-radius: 8px;
            margin: 20px auto;
            max-width: 800px;
        }
        .legend-color {
            display: inline-block;
            width: 20px;
            height: 12px;
            border-radius: 2px;
            margin-right: 5px;
        }
        .legend-text {
            font-size: 11px;
            color: #aaa;
        }
        .tooltip {
            position: relative;
            cursor: help;
        }
        .tooltip:hover::after {
            content: attr(data-tip);
            position: absolute;
            background: #333;
            color: #fff;
            padding: 5px 10px;
            border-radius: 4px;
            font-size: 10px;
            white-space: nowrap;
            z-index: 100;
            top: -25px;
            left: 50%;
            transform: translateX(-50%);
        }
        .report-actions {
            text-align: center;
            margin: 15px 0;
        }
        .report-actions button {
            margin: 0 5px;
            padding: 8px 16px;
            border-radius: 4px;
            border: none;
            cursor: pointer;
            font-weight: bold;
        }
        
        /* Tabs */
        .tabs {
            display: flex;
            gap: 0;
            margin-bottom: 20px;
            background: #2a2a2a;
            border-radius: 8px;
            overflow: hidden;
        }
        .tab-btn {
            padding: 12px 25px;
            background: #2a2a2a;
            color: #aaa;
            border: none;
            cursor: pointer;
            font-size: 14px;
            font-weight: bold;
            transition: all 0.3s;
        }
        .tab-btn:hover {
            background: #3a3a3a;
            color: #fff;
        }
        .tab-btn.active {
            background: #ffd700;
            color: #1a1a1a;
        }
        .tab-content {
            display: none;
        }
        .tab-content.active {
            display: block;
        }
    </style>
    <script>
        var TIMEZONE_OFFSET_MS = 3 * 60 * 60 * 1000;
        var treeVisible = false;
        var currentView = 'hourly';
        var selectedMeters = [];
        var currentTab = 'heatmap';
        var reportData = null;
        var isLoadingReport = false;

        function formatDateLocal(ts) {
            var d = new Date(ts + TIMEZONE_OFFSET_MS);
            var year = d.getUTCFullYear();
            var month = ('0' + (d.getUTCMonth() + 1)).slice(-2);
            var day = ('0' + d.getUTCDate()).slice(-2);
            return year + '-' + month + '-' + day;
        }

        function getHourLocal(ts) {
            var d = new Date(ts + TIMEZONE_OFFSET_MS);
            return d.getUTCHours();
        }

        function getDayOfWeekLocal(ts) {
            var d = new Date(ts + TIMEZONE_OFFSET_MS);
            return d.getUTCDay();
        }

        function getWeekNumber(ts) {
            var d = new Date(ts + TIMEZONE_OFFSET_MS);
            d.setUTCHours(0, 0, 0, 0);
            d.setUTCDate(d.getUTCDate() + 3 - (d.getUTCDay() + 6) % 7);
            var week1 = new Date(d.getUTCFullYear(), 0, 4);
            return 1 + Math.round(((d.getTime() - week1.getTime()) / 86400000 - 3 + (week1.getUTCDay() + 6) % 7) / 7);
        }

        function getBasePath() {
            var serverUrl = window.location.protocol + '//' + window.location.host;
            return serverUrl + '/Energyheatmap/';
        }

        function calculatePercentile(values, percentile) {
            if (!values || values.length === 0) return 1;
            var sorted = values.slice().sort(function(a, b) { return a - b; });
            var index = Math.ceil((percentile / 100) * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
        }

        // ====== УПРАВЛЕНИЕ ВКЛАДКАМИ ======
        function switchTab(tabName) {
            currentTab = tabName;
            document.querySelectorAll('.tab-content').forEach(function(el) {
                el.classList.remove('active');
            });
            document.querySelectorAll('.tab-btn').forEach(function(el) {
                el.classList.remove('active');
            });
            document.getElementById('tab-' + tabName).classList.add('active');
            document.querySelector('.tab-btn[data-tab="' + tabName + '"]').classList.add('active');
            // НЕ вызываем loadReport() автоматически!
        }

        // ====== ФУНКЦИИ ДЛЯ ДЕРЕВА ======
        async function loadRoots() {
            var select = document.getElementById('rootSelect');
            select.innerHTML = '<option value="">Loading roots...</option>';
            var url = getBasePath() + 'tree/roots';
            try {
                var response = await fetch(url);
                if (!response.ok) throw new Error('HTTP ' + response.status);
                var roots = await response.json();
                select.innerHTML = '<option value="">-- Select root area --</option>';
                for (var i = 0; i < roots.length; i++) {
                    var option = document.createElement('option');
                    option.value = roots[i].path;
                    option.textContent = roots[i].displayName || roots[i].name;
                    select.appendChild(option);
                }
            } catch (err) {
                console.error(err);
                select.innerHTML = '<option value="">Failed to load roots</option>';
            }
        }

        function toggleTree() {
            var container = document.getElementById('treeContainer');
            if (container) {
                if (container.style.display === 'none') {
                    container.style.display = '';
                    treeVisible = true;
                } else {
                    container.style.display = 'none';
                    treeVisible = false;
                }
            }
        }

        function updateSelectionInfo() {
            var info = document.getElementById('selectionInfo');
            var clearBtn = document.getElementById('clearSelectionBtn');
            if (selectedMeters.length === 0) {
                info.innerHTML = 'No meters selected';
                if (clearBtn) clearBtn.style.display = 'none';
            } else {
                info.innerHTML = 'Selected: ' + selectedMeters.length + ' meter(s)';
                if (clearBtn) clearBtn.style.display = 'inline-block';
            }
        }

        function clearSelection() {
            var checkboxes = document.querySelectorAll('.tree-checkbox');
            for (var i = 0; i < checkboxes.length; i++) {
                checkboxes[i].checked = false;
            }
            selectedMeters = [];
            updateSelectionInfo();
        }

        function toggleFolderChildren(folderLi, isChecked) {
            var checkboxes = folderLi.querySelectorAll('.tree-checkbox');
            for (var i = 0; i < checkboxes.length; i++) {
                checkboxes[i].checked = isChecked;
                var meterId = checkboxes[i].getAttribute('data-id');
                if (meterId) {
                    if (isChecked) {
                        if (selectedMeters.indexOf(meterId) === -1) selectedMeters.push(meterId);
                    } else {
                        var index = selectedMeters.indexOf(meterId);
                        if (index !== -1) selectedMeters.splice(index, 1);
                    }
                }
            }
            updateSelectionInfo();
        }

        async function findMeters() {
            var rawPath = document.getElementById('treePath').value.trim();
            var masks = document.getElementById('masks').value;
            var treeContainer = document.getElementById('treeContainer');
            var errorDiv = document.getElementById('errorMsg');
            errorDiv.innerText = '';

            if (!rawPath || rawPath === '#') {
                alert('Please specify a tree path (e.g., "#tula" or "tula")');
                return;
            }

            var pathForServlet = rawPath;
            if (!pathForServlet.startsWith('ABSPATH:1:')) {
                if (!pathForServlet.startsWith('#')) pathForServlet = '#' + pathForServlet;
                pathForServlet = 'ABSPATH:1:' + pathForServlet;
            }

            treeContainer.innerHTML = '<div style="padding: 10px;">Loading tree...</div>';
            treeContainer.style.display = '';
            treeVisible = true;
            var url = getBasePath() + 'meters/find?path=' + encodeURIComponent(pathForServlet) + '&masks=' + encodeURIComponent(masks);
            try {
                var response = await fetch(url);
                if (!response.ok) throw new Error('HTTP ' + response.status);
                var meters = await response.json();
                if (meters.length === 0) {
                    treeContainer.innerHTML = '<div style="padding: 10px;">No meters found</div>';
                    return;
                }

                var root = { children: {} };
                for (var i = 0; i < meters.length; i++) {
                    var m = meters[i];
                    var displayPath = m.displayPath || m.name;
                    var segments = displayPath.split(' / ');
                    var current = root;
                    for (var j = 0; j < segments.length; j++) {
                        var seg = segments[j];
                        if (!current.children[seg]) current.children[seg] = { name: seg, children: {} };
                        current = current.children[seg];
                    }
                    current.id = m.id;
                }

                function renderTree(node, level) {
                    if (node.name && level > 0) {
                        var li = document.createElement('li');
                        
                        var checkbox = document.createElement('input');
                        checkbox.type = 'checkbox';
                        checkbox.className = 'tree-checkbox';
                        if (node.id) {
                            checkbox.setAttribute('data-id', node.id);
                            checkbox.addEventListener('change', function(e, meterId) {
                                return function(ev) {
                                    if (ev.target.checked) {
                                        if (selectedMeters.indexOf(meterId) === -1) selectedMeters.push(meterId);
                                    } else {
                                        var index = selectedMeters.indexOf(meterId);
                                        if (index !== -1) selectedMeters.splice(index, 1);
                                    }
                                    updateSelectionInfo();
                                };
                            }(null, node.id));
                        } else {
                            checkbox.addEventListener('change', function(e, folderLi) {
                                return function(ev) {
                                    toggleFolderChildren(folderLi, ev.target.checked);
                                };
                            }(null, li));
                        }
                        
                        var span = document.createElement('span');
                        span.className = 'node ' + (node.id ? 'leaf-icon' : 'folder-icon');
                        span.textContent = node.name;
                        if (node.id) {
                            var meterId = node.id;
                            span.addEventListener('click', function(e, id) {
                                return function() {
                                    var selected = document.querySelectorAll('.tree .selected');
                                    for (var s = 0; s < selected.length; s++) selected[s].classList.remove('selected');
                                    span.classList.add('selected');
                                    document.getElementById('trendId').value = id;
                                    if (currentTab === 'heatmap') {
                                        loadHeatmap();
                                    }
                                };
                            }(null, meterId));
                        } else {
                            span.addEventListener('click', function(liElement) {
                                return function(e) {
                                    var childUl = liElement.querySelector('ul');
                                    if (childUl) childUl.style.display = childUl.style.display === 'none' ? '' : 'none';
                                };
                            }(li));
                        }
                        
                        li.appendChild(checkbox);
                        li.appendChild(span);
                        
                        var childrenNames = Object.keys(node.children);
                        if (childrenNames.length) {
                            var ul = document.createElement('ul');
                            for (var k = 0; k < childrenNames.length; k++) {
                                var name = childrenNames[k];
                                ul.appendChild(renderTree(node.children[name], level + 1));
                            }
                            li.appendChild(ul);
                        }
                        return li;
                    } else {
                        var rootUl = document.createElement('ul');
                        rootUl.className = 'tree';
                        var childrenNames = Object.keys(node.children);
                        for (var k = 0; k < childrenNames.length; k++) {
                            var name = childrenNames[k];
                            rootUl.appendChild(renderTree(node.children[name], 1));
                        }
                        return rootUl;
                    }
                }

                var treeDom = renderTree(root, 0);
                treeContainer.innerHTML = '';
                treeContainer.appendChild(treeDom);
                if (!treeVisible) treeContainer.style.display = 'none';
                
                selectedMeters = [];
                updateSelectionInfo();
            } catch (err) {
                console.error(err);
                treeContainer.innerHTML = '<div style="padding: 10px;">Failed to load meters: ' + err.message + '</div>';
            }
        }

        // ====== ФУНКЦИИ ДЛЯ HEATMAP ======
        function switchView() {
            var selector = document.getElementById('viewSelector');
            currentView = selector.value;
            var trendId = document.getElementById('trendId').value;
            if (trendId && trendId !== '') {
                loadHeatmap();
            }
            var modeInfo = document.getElementById('modeInfo');
            if (modeInfo) {
                if (currentView === 'hourly') {
                    modeInfo.innerHTML = 'Hourly mode: rows = days, columns = hours (0-23)';
                } else {
                    modeInfo.innerHTML = 'Daily mode: rows = weeks, columns = weekdays (Mon-Sun), color = daily total';
                }
            }
        }

        async function loadHeatmap() {
            var id = document.getElementById('trendId').value;
            var start = document.getElementById('startDate').value;
            var end = document.getElementById('endDate').value;
            var errorDiv = document.getElementById('errorMsg');
            errorDiv.innerText = '';
            
            if (!id || id === '' || id === 'undefined') {
                errorDiv.innerText = "Please select a meter from the tree first";
                return;
            }
            if (!start || !end) {
                errorDiv.innerText = "Please select start and end dates";
                return;
            }
            
            var url = getBasePath() + 'data?id=' + encodeURIComponent(id) + '&start=' + start + '&end=' + end + '&aggregation=hourly&mode=consumption';
            try {
                var response = await fetch(url);
                var text = await response.text();
                var json;
                try { json = JSON.parse(text); } catch(e) { errorDiv.innerText = "Invalid JSON: " + e.message; return; }
                if (json.error) { errorDiv.innerText = json.error; return; }
                if (!json.data || !Array.isArray(json.data)) { errorDiv.innerText = "No data array in response"; return; }
                
                if (currentView === 'hourly') {
                    renderHourlyHeatmap(json.data, false);
                } else {
                    renderDailyHeatmap(json.data, false);
                }
            } catch (err) {
                console.error(err);
                errorDiv.innerText = "Network error: " + err.message;
            }
        }

        async function loadAggregatedHeatmap() {
            if (selectedMeters.length === 0) {
                document.getElementById('errorMsg').innerText = "Please select at least one meter";
                return;
            }
            
            var start = document.getElementById('startDate').value;
            var end = document.getElementById('endDate').value;
            var errorDiv = document.getElementById('errorMsg');
            errorDiv.innerText = '';
            
            if (!start || !end) {
                errorDiv.innerText = "Please select start and end dates";
                return;
            }
            
            var container = document.getElementById('heatmapContainer');
            container.innerHTML = '<div class="footer">Loading aggregated data for ' + selectedMeters.length + ' meters...</div>';
            
            var url = getBasePath() + 'data/aggregate?ids=' + encodeURIComponent(selectedMeters.join(',')) + '&start=' + start + '&end=' + end;
            try {
                var response = await fetch(url);
                var text = await response.text();
                var json;
                try { json = JSON.parse(text); } catch(e) { errorDiv.innerText = "Invalid JSON: " + e.message; return; }
                if (json.error) { errorDiv.innerText = json.error; return; }
                if (!json.data || !Array.isArray(json.data)) { errorDiv.innerText = "No data array in response"; return; }
                
                if (currentView === 'hourly') {
                    renderHourlyHeatmap(json.data, true);
                } else {
                    renderDailyHeatmap(json.data, true);
                }
            } catch (err) {
                console.error(err);
                errorDiv.innerText = "Network error: " + err.message;
            }
        }

        function renderHourlyHeatmap(dataPoints, isAggregated) {
            var container = document.getElementById('heatmapContainer');
            if (!dataPoints || dataPoints.length === 0) {
                container.innerHTML = '<p class="error">No data points</p>';
                return;
            }

            var daysMap = new Map();
            var allValues = [];
            var totalSum = 0;
            
            for (var i = 0; i < dataPoints.length; i++) {
                var p = dataPoints[i];
                var dayKey = formatDateLocal(p.ts);
                var hour = getHourLocal(p.ts);
                if (!daysMap.has(dayKey)) {
                    daysMap.set(dayKey, new Array(24).fill(null));
                }
                daysMap.get(dayKey)[hour] = p.value;
                if (p.value !== null && !isNaN(p.value)) {
                    allValues.push(p.value);
                    totalSum += p.value;
                }
            }

            var sortedDays = Array.from(daysMap.keys()).sort();
            if (sortedDays.length === 0) {
                container.innerHTML = '<p class="error">No days with data</p>';
                return;
            }

            var PERCENTILE = 95;
            var percentileValue = calculatePercentile(allValues, PERCENTILE);
            var minVal = Math.min.apply(null, allValues);
            if (minVal === Infinity) minVal = 0;
            var maxVal = percentileValue;
            
            if (maxVal === minVal) {
                maxVal = minVal + 1;
            }

            function getColor(value) {
                if (value === null) return "#333333";
                if (value > maxVal) return "#ff0000";
                var t = (value - minVal) / (maxVal - minVal);
                t = Math.min(1, Math.max(0, t));
                var r = Math.min(255, Math.floor(255 * t * 2));
                var g = Math.min(255, Math.floor(255 * (2 - t * 2)));
                return 'rgb(' + r + ', ' + g + ', 0)';
            }

            var table = document.createElement('table');
            var thead = document.createElement('thead');
            var headerRow = document.createElement('tr');
            
            var th1 = document.createElement('th');
            if (isAggregated) {
                th1.textContent = 'Day / Hour (Aggregated - ' + selectedMeters.length + ' meters)';
            } else {
                th1.textContent = 'Day / Hour';
            }
            headerRow.appendChild(th1);
            
            for (var h = 0; h < 24; h++) {
                var th = document.createElement('th');
                th.textContent = h + ':00';
                headerRow.appendChild(th);
            }
            thead.appendChild(headerRow);
            table.appendChild(thead);
            
            var tbody = document.createElement('tbody');
            for (var d = 0; d < sortedDays.length; d++) {
                var day = sortedDays[d];
                var row = daysMap.get(day);
                var tr = document.createElement('tr');
                
                var tdDay = document.createElement('td');
                tdDay.className = 'hour-col';
                tdDay.textContent = day;
                tr.appendChild(tdDay);
                
                for (var h = 0; h < 24; h++) {
                    var val = row[h];
                    var color = getColor(val);
                    var display = (val !== null) ? val.toFixed(1) : '-';
                    var td = document.createElement('td');
                    td.style.backgroundColor = color;
                    td.style.color = 'white';
                    td.textContent = display;
                    td.title = (val !== null) ? display + ' kWh' : 'No data';
                    tr.appendChild(td);
                }
                tbody.appendChild(tr);
            }
            table.appendChild(tbody);
            
            container.innerHTML = '';
            container.appendChild(table);
            
            var totalDiv = document.createElement('div');
            totalDiv.className = 'total-summary';
            if (isAggregated) {
                totalDiv.innerHTML = '<div>Total aggregated consumption</div>' +
                                     '<div class="total-value">' + totalSum.toFixed(1) + ' kWh</div>' +
                                     '<div style="font-size:10px;color:#888;">Sum of ' + selectedMeters.length + ' meters</div>';
            } else {
                totalDiv.innerHTML = '<div>Total consumption for selected period</div>' +
                                     '<div class="total-value">' + totalSum.toFixed(1) + ' kWh</div>' +
                                     '<div style="font-size:10px;color:#888;">Sum of all displayed cells</div>';
            }
            container.appendChild(totalDiv);
            
            document.getElementById('legendMin').innerText = minVal.toFixed(1);
            document.getElementById('legendMax').innerText = maxVal.toFixed(1);
        }

        function renderDailyHeatmap(dataPoints, isAggregated) {
            var container = document.getElementById('heatmapContainer');
            if (!dataPoints || dataPoints.length === 0) {
                container.innerHTML = '<p class="error">No data points</p>';
                return;
            }

            var dailyTotals = new Map();
            var dailyValues = [];
            var totalSum = 0;
            
            for (var i = 0; i < dataPoints.length; i++) {
                var p = dataPoints[i];
                var dayKey = formatDateLocal(p.ts);
                if (p.value !== null && !isNaN(p.value)) {
                    if (!dailyTotals.has(dayKey)) {
                        dailyTotals.set(dayKey, 0);
                    }
                    dailyTotals.set(dayKey, dailyTotals.get(dayKey) + p.value);
                    totalSum += p.value;
                }
            }
            
            for (var total of dailyTotals.values()) {
                dailyValues.push(total);
            }
            
            if (dailyValues.length === 0) {
                container.innerHTML = '<p class="error">No daily data available</p>';
                return;
            }
            
            var weeksMap = new Map();
            var weekLabels = new Map();
            
            for (var [day, total] of dailyTotals) {
                var ts = new Date(day + 'T12:00:00Z').getTime();
                var weekNum = getWeekNumber(ts);
                var year = new Date(ts + TIMEZONE_OFFSET_MS).getUTCFullYear();
                var weekKey = year + '-W' + weekNum;
                var sortKey = year * 100 + weekNum;
                var dayOfWeek = getDayOfWeekLocal(ts);
                var colIndex = (dayOfWeek === 0) ? 6 : dayOfWeek - 1;
                
                if (!weeksMap.has(weekKey)) {
                    weeksMap.set(weekKey, { 
                        data: new Array(7).fill(null),
                        sortKey: sortKey,
                        year: year,
                        weekNum: weekNum
                    });
                    var startDate = new Date(ts + TIMEZONE_OFFSET_MS);
                    var dayOffset = (startDate.getUTCDay() + 6) % 7;
                    startDate.setUTCDate(startDate.getUTCDate() - dayOffset);
                    var startStr = startDate.toISOString().slice(0, 10);
                    weekLabels.set(weekKey, startStr);
                }
                weeksMap.get(weekKey).data[colIndex] = total;
            }
            
            var sortedWeeks = Array.from(weeksMap.keys()).sort(function(a, b) {
                return weeksMap.get(a).sortKey - weeksMap.get(b).sortKey;
            });
            
            var PERCENTILE = 95;
            var percentileValue = calculatePercentile(dailyValues, PERCENTILE);
            var minVal = Math.min.apply(null, dailyValues);
            if (minVal === Infinity) minVal = 0;
            var maxVal = percentileValue;
            
            if (maxVal === minVal) {
                maxVal = minVal + 1;
            }
            
            function getColor(value) {
                if (value === null) return "#333333";
                if (value > maxVal) return "#ff0000";
                var t = (value - minVal) / (maxVal - minVal);
                t = Math.min(1, Math.max(0, t));
                var r = Math.min(255, Math.floor(255 * t * 2));
                var g = Math.min(255, Math.floor(255 * (2 - t * 2)));
                return 'rgb(' + r + ', ' + g + ', 0)';
            }
            
            var dayNames = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
            
            var table = document.createElement('table');
            var thead = document.createElement('thead');
            var headerRow = document.createElement('tr');
            
            var th1 = document.createElement('th');
            if (isAggregated) {
                th1.textContent = 'Week (Aggregated - ' + selectedMeters.length + ' meters)';
            } else {
                th1.textContent = 'Week (starting Mon)';
            }
            headerRow.appendChild(th1);
            
            for (var d = 0; d < 7; d++) {
                var th = document.createElement('th');
                th.textContent = dayNames[d];
                headerRow.appendChild(th);
            }
            thead.appendChild(headerRow);
            table.appendChild(thead);
            
            var tbody = document.createElement('tbody');
            for (var w = 0; w < sortedWeeks.length; w++) {
                var weekKey = sortedWeeks[w];
                var weekStart = weekLabels.get(weekKey);
                var weekData = weeksMap.get(weekKey);
                var weekNumDisplay = weekData.weekNum;
                var yearDisplay = weekData.year;
                var row = weekData.data;
                
                var tr = document.createElement('tr');
                var tdWeek = document.createElement('td');
                tdWeek.className = 'week-col';
                tdWeek.innerHTML = weekStart + '<br><span style="font-size:9px;">(Week ' + weekNumDisplay + ', ' + yearDisplay + ')</span>';
                tr.appendChild(tdWeek);
                
                for (var d = 0; d < 7; d++) {
                    var val = row[d];
                    var color = getColor(val);
                    var display = (val !== null) ? val.toFixed(1) : '-';
                    var td = document.createElement('td');
                    td.style.backgroundColor = color;
                    td.style.color = 'white';
                    td.textContent = display;
                    td.title = (val !== null) ? display + ' kWh' : 'No data';
                    tr.appendChild(td);
                }
                tbody.appendChild(tr);
            }
            table.appendChild(tbody);
            
            container.innerHTML = '';
            container.appendChild(table);
            
            var totalDiv = document.createElement('div');
            totalDiv.className = 'total-summary';
            if (isAggregated) {
                totalDiv.innerHTML = '<div>Total aggregated consumption</div>' +
                                     '<div class="total-value">' + totalSum.toFixed(1) + ' kWh</div>' +
                                     '<div style="font-size:10px;color:#888;">Sum of ' + selectedMeters.length + ' meters</div>';
            } else {
                totalDiv.innerHTML = '<div>Total consumption for selected period</div>' +
                                     '<div class="total-value">' + totalSum.toFixed(1) + ' kWh</div>' +
                                     '<div style="font-size:10px;color:#888;">Sum of all displayed cells</div>';
            }
            container.appendChild(totalDiv);
            
            document.getElementById('legendMin').innerText = minVal.toFixed(1);
            document.getElementById('legendMax').innerText = maxVal.toFixed(1);
        }

        // ====== ФУНКЦИИ ДЛЯ ОТЧЁТА ======
        async function loadReport() {
            if (isLoadingReport) {
                console.log('Report already loading, please wait...');
                return;
            }
            isLoadingReport = true;
            
            try {
                var path = document.getElementById('treePath').value.trim();
                var months = document.getElementById('monthsCount').value || 1;
                var container = document.getElementById('reportContainer');
                var errorDiv = document.getElementById('errorMsg');
                
                if (!path || path === '#') {
                    container.innerHTML = '<div class="footer">Please select a hypermarket using the tree above</div>';
                    return;
                }
                
                errorDiv.innerText = '';
                container.innerHTML = '<div class="footer">Loading report...</div>';
                
                var url = getBasePath() + 'report/calculated-percent?path=' + encodeURIComponent(path) + '&months=' + months;
                console.log('Fetching report:', url);
                
                var response = await fetch(url);
                var text = await response.text();
                var json;
                try { json = JSON.parse(text); } catch(e) { errorDiv.innerText = "Invalid JSON: " + e.message; return; }
                if (json.error) { errorDiv.innerText = json.error; return; }
                
                reportData = json;
                renderReport(json);
            } catch (err) {
                console.error(err);
                errorDiv.innerText = "Network error: " + err.message;
                container.innerHTML = '';
            } finally {
                isLoadingReport = false;
            }
        }

        function renderReport(data) {
            var container = document.getElementById('reportContainer');
            if (!data || data.length === 0) {
                container.innerHTML = '<div class="error">No data available for this period</div>';
                return;
            }

            var monthsCount = parseInt(document.getElementById('monthsCount').value) || 1;

            // Фильтруем и сортируем данные (новые → старые)
            var filteredData = [];
            for (var groupData of data) {
                var sortedMonths = groupData.data.slice().sort(function(a, b) {
                    if (a.year !== b.year) return b.year - a.year;
                    return b.month - a.month;
                });
                var filteredGroup = {
                    group: groupData.group,
                    mask: groupData.mask,
                    data: sortedMonths.slice(0, monthsCount)
                };
                filteredData.push(filteredGroup);
            }

            var months = [];
            if (filteredData.length > 0 && filteredData[0].data) {
                for (var m of filteredData[0].data) {
                    months.push({ year: m.year, month: m.month });
                }
            }

            var monthLabels = [];
            for (var m of months) {
                monthLabels.push(m.year + '-' + String(m.month).padStart(2, '0'));
            }

            var html = '<div class="summary-block">';
            html += '<h3 style="color:#ffd700; text-align:center;">Calculated Data Report</h3>';
            html += '<div style="text-align:center; font-size:11px; color:#aaa; margin-bottom:10px;">';
            html += 'Path: <span style="color:#ffd700;">' + document.getElementById('treePath').value + '</span>';
            html += ' | Period: ' + monthsCount + ' months</div>';
            html += '<table class="report-table"><thead><tr><th>Group</th>';
            for (var label of monthLabels) {
                html += '<th>' + label + '</th>';
            }
            html += '<th>Avg %</th></tr></thead><tbody>';

            var overallTotal = 0;
            var overallCalculated = 0;

            for (var groupData of filteredData) {
                var groupName = groupData.group;
                var groupMonths = groupData.data || [];
                var totalConsumption = 0;
                var totalCalculated = 0;
                
                html += '<tr><td><strong>' + groupName + '</strong></td>';
                
                var monthlyValues = [];
                for (var m of groupMonths) {
                    var percent = m.calculatedPercent || 0;
                    var total = m.totalConsumption || 0;
                    var calculated = m.calculatedConsumption || 0;
                    
                    totalConsumption += total;
                    totalCalculated += calculated;
                    overallTotal += total;
                    overallCalculated += calculated;
                    
                    var colorClass = 'calculated-none';
                    var display = '0%';
                    if (percent > 0) {
                        display = percent.toFixed(1) + '%';
                        if (percent > 50) colorClass = 'calculated-high';
                        else if (percent > 10) colorClass = 'calculated-medium';
                        else colorClass = 'calculated-low';
                    }
                    
                    var tooltip = 'Total: ' + total.toFixed(1) + ' kWh, Calculated: ' + calculated.toFixed(1) + ' kWh';
                    html += '<td class="' + colorClass + ' tooltip" data-tip="' + tooltip + '">' + display + '</td>';
                    monthlyValues.push(percent);
                }
                
                var avgPercent = 0;
                if (groupMonths.length > 0) {
                    var sum = 0;
                    for (var v of monthlyValues) {
                        sum += v;
                    }
                    avgPercent = sum / groupMonths.length;
                }
                html += '<td>' + avgPercent.toFixed(1) + '%</td>';
                html += '</tr>';
            }

            html += '</tbody></table>';

            var overallPercent = 0;
            if (overallTotal > 0) {
                overallPercent = (overallCalculated / overallTotal) * 100;
            }
            html += '<div style="text-align:center; margin-top:15px; padding:10px; background:#1a1a1a; border-radius:4px;">';
            html += '<span style="color:#ffd700;">Overall (last ' + monthsCount + ' months):</span> ';
            html += '<span style="color:#fff;">' + overallTotal.toFixed(1) + ' kWh total</span> | ';
            html += '<span style="color:#ff6666;">' + overallCalculated.toFixed(1) + ' kWh calculated</span> | ';
            html += '<span style="color:#ffd700;">' + overallPercent.toFixed(1) + '% calculated</span>';
            html += '</div></div>';

            html += '<div style="text-align:center; margin:10px auto;">';
            html += '<span class="legend-color" style="background:#006400;"></span><span class="legend-text">0-10%</span> ';
            html += '<span class="legend-color" style="background:#b8860b;"></span><span class="legend-text">10-50%</span> ';
            html += '<span class="legend-color" style="background:#8b0000;"></span><span class="legend-text">&gt;50%</span> ';
            html += '<span class="legend-color" style="background:#2a2a2a;"></span><span class="legend-text">No data</span>';
            html += '</div>';

            html += '<div class="report-actions">';
            html += '<button class="export-btn" onclick="exportCSV()">Export CSV</button>';
            html += '<button class="export-btn" style="background:#4CAF50;" onclick="loadReport()">Refresh Report</button>';
            html += '</div>';

            container.innerHTML = html;
        }

        function exportCSV() {
            if (!reportData || reportData.length === 0) {
                alert('No data to export');
                return;
            }

            var monthsCount = parseInt(document.getElementById('monthsCount').value) || 1;
            
            var filteredData = [];
            for (var groupData of reportData) {
                var sortedMonths = groupData.data.slice().sort(function(a, b) {
                    if (a.year !== b.year) return b.year - a.year;
                    return b.month - a.month;
                });
                var filteredGroup = {
                    group: groupData.group,
                    mask: groupData.mask,
                    data: sortedMonths.slice(0, monthsCount)
                };
                filteredData.push(filteredGroup);
            }

            var csv = 'Group';
            var months = [];
            if (filteredData.length > 0 && filteredData[0].data) {
                for (var m of filteredData[0].data) {
                    months.push(m.year + '-' + String(m.month).padStart(2, '0'));
                }
            }
            for (var label of months) {
                csv += ',' + label;
            }
            csv += ',Average %\n';

            var overallTotal = 0;
            var overallCalculated = 0;

            for (var groupData of filteredData) {
                var groupName = groupData.group;
                csv += '"' + groupName + '"';
                var groupMonths = groupData.data || [];
                var sum = 0;
                var count = 0;
                
                for (var m of groupMonths) {
                    var percent = m.calculatedPercent || 0;
                    csv += ',' + percent.toFixed(1);
                    sum += percent;
                    count++;
                    overallTotal += m.totalConsumption || 0;
                    overallCalculated += m.calculatedConsumption || 0;
                }
                var avgPercent = count > 0 ? sum / count : 0;
                csv += ',' + avgPercent.toFixed(1) + '\n';
            }

            var overallPercent = overallTotal > 0 ? (overallCalculated / overallTotal) * 100 : 0;
            csv += '"OVERALL (last ' + monthsCount + ' months)",';
            for (var i = 0; i < months.length; i++) {
                csv += ',';
            }
            csv += overallPercent.toFixed(1) + '\n';

            var blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' });
            var link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = 'calculated_report_' + new Date().toISOString().slice(0,10) + '.csv';
            link.click();
            URL.revokeObjectURL(link.href);
        }

        // ====== ИНИЦИАЛИЗАЦИЯ ======
        document.addEventListener('DOMContentLoaded', function() {
            loadRoots();
            var treeContainer = document.getElementById('treeContainer');
            if (treeContainer) treeContainer.style.display = 'none';
            var rootSelect = document.getElementById('rootSelect');
            if (rootSelect) {
                rootSelect.addEventListener('change', function() {
                    if (this.value) {
                        document.getElementById('treePath').value = this.value;
                        findMeters();
                    }
                });
            }
            
            document.querySelector('.tab-btn[data-tab="report"]').addEventListener('click', function() {
                switchTab('report');
            });
            document.querySelector('.tab-btn[data-tab="heatmap"]').addEventListener('click', function() {
                switchTab('heatmap');
            });
        });
    </script>
</head>
<body>
<h1>Energy Heatmap & Report</h1>

<!-- Вкладки -->
<div class="tabs">
    <button class="tab-btn active" data-tab="heatmap" onclick="switchTab('heatmap')">🔥 Heatmap</button>
    <button class="tab-btn" data-tab="report" onclick="switchTab('report')">📊 Calculated Data Report</button>
</div>

<!-- ====== ВКЛАДКА HEATMAP ====== -->
<div id="tab-heatmap" class="tab-content active">
    <div class="controls">
        <label>Root area:</label>
        <select id="rootSelect" style="min-width: 150px;"><option value="">-- Load roots --</option></select>
        <label>Tree path:</label>
        <input type="text" id="treePath" value="#tula" size="30" placeholder="#tula, tula, ABSPATH:1:#tula" />
        <label>Masks (comma):</label>
        <input type="text" id="masks" value="_e1" size="15" />
        <button onclick="findMeters()">Find meters</button>
    </div>

    <div class="controls">
        <label>Meter tree:</label>
        <button class="toggle-tree-btn" onclick="toggleTree()">Toggle tree</button>
        <label>Start:</label>
        <input type="date" id="startDate" value="2026-05-01" />
        <label>End:</label>
        <input type="date" id="endDate" value="2026-05-02" />
        <label>View mode:</label>
        <select id="viewSelector" class="view-selector" onchange="switchView()">
            <option value="hourly">Hourly (Time of day)</option>
            <option value="daily">Daily (Week view)</option>
        </select>
        <button onclick="loadHeatmap()">Show Heatmap</button>
    </div>

    <input type="hidden" id="trendId" value="" />

    <div id="treeContainer" class="tree-container"></div>

    <div class="selection-bar">
        <div class="selection-info">
            <span id="selectionInfo">No meters selected</span>
        </div>
        <div>
            <button id="clearSelectionBtn" onclick="clearSelection()" style="display:none; background:#555; color:white; border:none; border-radius:4px; padding:4px 10px; margin-right:10px; cursor:pointer;">Clear</button>
            <button class="aggregate-btn" onclick="loadAggregatedHeatmap()">Show Aggregated (Sum)</button>
        </div>
    </div>

    <div class="legend">
        <span>LOW</span>
        <div class="legend-colors"></div>
        <span>HIGH</span>
    </div>
    <div class="legend-values">
        <span id="legendMin">0</span>
        <span id="legendMax">100</span>
    </div>

    <div id="errorMsg" class="error"></div>
    <div id="heatmapContainer" style="overflow-x: auto;"><div class="footer">Select a meter and click "Show Heatmap"</div></div>
    <div class="footer">Hover over cells to see exact consumption (kWh). Colors are scaled using 95th percentile. Red cells indicate outliers.</div>
    <div id="modeInfo" class="mode-info">Hourly mode: rows = days, columns = hours (0-23)</div>
</div>

<a href="settings.jsp" style="color: #ffd700; text-decoration: none; font-size: 14px; margin-left: 20px;">⚙️ Settings</a>

<!-- ====== ВКЛАДКА REPORT ====== -->
<div id="tab-report" class="tab-content">
    <div class="controls">
        <label>Months:</label>
        <input type="number" id="monthsCount" value="1" min="1" max="24" style="width:60px;" />
        <button onclick="loadReport()">Generate Report</button>
        <span style="color:#aaa; font-size:11px;">(uses selected tree path)</span>
    </div>
    <div id="reportContainer">
        <div class="footer">Select a hypermarket using the tree on the "Heatmap" tab, then click "Generate Report"</div>
    </div>
</div>

</body>
</html>