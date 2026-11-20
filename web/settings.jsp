<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
<head>
    <title>Settings - Energy Heatmap</title>
    <meta charset="UTF-8">
    <style>
        body {
            background: #1a1a1a;
            color: #f0f0f0;
            font-family: Arial, sans-serif;
            margin: 20px;
        }
        h1 {
            color: #ffd700;
            text-align: center;
        }
        .container {
            max-width: 600px;
            margin: 0 auto;
            background: #2a2a2a;
            padding: 20px;
            border-radius: 8px;
        }
        .setting-group {
            margin-bottom: 20px;
            padding: 15px;
            background: #1a1a1a;
            border-radius: 4px;
        }
        .setting-group label {
            display: block;
            color: #ffd700;
            margin-bottom: 5px;
            font-weight: bold;
        }
        .setting-group .description {
            font-size: 11px;
            color: #888;
            margin-bottom: 8px;
        }
        .setting-group input[type="number"],
        .setting-group input[type="text"] {
            width: 100%;
            padding: 8px 12px;
            border-radius: 4px;
            border: none;
            background: #3a3a3a;
            color: white;
            box-sizing: border-box;
        }
        .setting-group input[type="checkbox"] {
            width: 20px;
            height: 20px;
            cursor: pointer;
        }
        .checkbox-group {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .buttons {
            display: flex;
            gap: 10px;
            justify-content: center;
            margin-top: 20px;
        }
        .buttons button {
            padding: 10px 25px;
            border-radius: 4px;
            border: none;
            cursor: pointer;
            font-weight: bold;
            font-size: 14px;
        }
        .btn-save {
            background: #ffd700;
            color: #1a1a1a;
        }
        .btn-save:hover {
            background: #e6c200;
        }
        .btn-load {
            background: #4CAF50;
            color: white;
        }
        .btn-load:hover {
            background: #45a049;
        }
        #status {
            text-align: center;
            margin-top: 15px;
            padding: 10px;
            border-radius: 4px;
        }
        .status-success {
            background: #1a3a1a;
            color: #00ff00;
        }
        .status-error {
            background: #3a1a1a;
            color: #ff4444;
        }
        .status-info {
            background: #1a2a3a;
            color: #44aaff;
        }
        .data-dir {
            font-size: 11px;
            color: #888;
            text-align: center;
            margin-top: 15px;
            padding: 10px;
            background: #1a1a1a;
            border-radius: 4px;
            word-break: break-all;
        }
        .back-link {
            display: block;
            text-align: center;
            margin-top: 20px;
            color: #aaa;
            text-decoration: none;
        }
        .back-link:hover {
            color: #ffd700;
        }
    </style>
    <script>
        function getBasePath() {
            var serverUrl = window.location.protocol + '//' + window.location.host;
            return serverUrl + '/Energyheatmap/';
        }

        async function loadSettings() {
            var status = document.getElementById('status');
            status.className = '';
            status.innerHTML = 'Loading settings...';
            
            var url = getBasePath() + 'settings?action=load';
            try {
                var response = await fetch(url);
                if (!response.ok) throw new Error('HTTP ' + response.status);
                var data = await response.json();
                
                document.getElementById('minDaysForMedian').value = data.minDaysForMedian || 3;
                document.getElementById('statusTrendName').value = data.statusTrendName || 'com_st_min';
                document.getElementById('statusErrorOnZero').checked = data.statusErrorOnZero !== false;
                document.getElementById('frozenRunHours').value = data.frozenRunHours || 12;
                document.getElementById('zeroThreshold').value = data.zeroThreshold || 100;
                document.getElementById('anomalyCorrectionEnabled').checked = data.anomalyCorrectionEnabled !== false;
                document.getElementById('debugLoggingEnabled').checked = data.debugLoggingEnabled === true;
                document.getElementById('resetThreshold').value = data.resetThreshold || 10000;
                document.getElementById('dataDirDisplay').textContent = data.dataDir || 'Not set';
                
                status.className = 'status-success';
                status.innerHTML = '✅ Settings loaded successfully';
            } catch (err) {
                console.error(err);
                status.className = 'status-error';
                status.innerHTML = '❌ Failed to load settings: ' + err.message;
            }
        }

        async function saveSettings() {
            var status = document.getElementById('status');
            status.className = '';
            status.innerHTML = 'Saving settings...';
            
            var params = new URLSearchParams();
            params.append('action', 'save');
            params.append('minDaysForMedian', document.getElementById('minDaysForMedian').value);
            params.append('statusTrendName', document.getElementById('statusTrendName').value);
            params.append('statusErrorOnZero', document.getElementById('statusErrorOnZero').checked);
            params.append('frozenRunHours', document.getElementById('frozenRunHours').value);
            params.append('zeroThreshold', document.getElementById('zeroThreshold').value);
            params.append('anomalyCorrectionEnabled', document.getElementById('anomalyCorrectionEnabled').checked);
            params.append('debugLoggingEnabled', document.getElementById('debugLoggingEnabled').checked);
            params.append('resetThreshold', document.getElementById('resetThreshold').value);
            
            var url = getBasePath() + 'settings?' + params.toString();
            try {
                var response = await fetch(url, { method: 'POST' });
                if (!response.ok) {
                    var text = await response.text();
                    throw new Error('HTTP ' + response.status + ': ' + text);
                }
                var data = await response.json();
                
                if (data.success) {
                    status.className = 'status-success';
                    status.innerHTML = '✅ ' + data.message + ' (data directory: ' + data.dataDir + ')';
                    // Обновляем путь к данным
                    if (data.dataDir) {
                        document.getElementById('dataDirDisplay').textContent = data.dataDir;
                    }
                } else {
                    throw new Error(data.message || 'Unknown error');
                }
            } catch (err) {
                console.error(err);
                status.className = 'status-error';
                status.innerHTML = '❌ Failed to save settings: ' + err.message;
            }
        }

        document.addEventListener('DOMContentLoaded', function() {
            loadSettings();
        });
    </script>
</head>
<body>
    <h1>🔧 Settings Manager</h1>
    
    <div class="container">
        <div id="status" class="status-info">Loading settings...</div>
        
        <div class="setting-group">
            <label>Min Days for Median</label>
            <div class="description">Minimum number of days required to calculate median for anomaly correction</div>
            <input type="number" id="minDaysForMedian" min="1" max="30" />
        </div>
        
        <div class="setting-group">
            <label>Status Trend Name</label>
            <div class="description">Trend that reports meter communication status. Used for monthly calculated-percent report and for filling gaps on the heatmap. Default: com_st_min</div>
            <input type="text" id="statusTrendName" />
        </div>
        
        <div class="setting-group">
            <label>Frozen Readings Run (hours)</label>
            <div class="description">How many consecutive hours without growth of the cumulative reading count as "no connection" at the month boundary. Default: 12</div>
            <input type="number" id="frozenRunHours" min="1" max="240" />
        </div>
        
        <div class="setting-group">
            <label>Status 0 Means No Connection</label>
            <div class="description">Checked: value 0 in the status trend means "no connection", 1 (or more) means "OK". Unchecked: the other way round.</div>
            <div class="checkbox-group">
                <input type="checkbox" id="statusErrorOnZero" />
                <span>0 = no connection</span>
            </div>
        </div>
        
        <div class="setting-group">
            <label>Zero Threshold</label>
            <div class="description">Kept for compatibility: gap filling is now driven by the status trend, so this threshold is not used in calculations any more. Default: 100.0</div>
            <input type="number" id="zeroThreshold" step="0.1" />
        </div>
        
        <div class="setting-group">
            <label>Reset Threshold</label>
            <div class="description">Kept for compatibility: counter resets are now detected by a drop of the cumulative reading, so this threshold is not used in calculations any more. Default: 10000.0</div>
            <input type="number" id="resetThreshold" step="0.1" />
        </div>
        
        <div class="setting-group">
            <label>Anomaly Correction</label>
            <div class="description">Enable correction of anomalous values using median</div>
            <div class="checkbox-group">
                <input type="checkbox" id="anomalyCorrectionEnabled" />
                <span>Enabled</span>
            </div>
        </div>
        
        <div class="setting-group">
            <label>Debug Logging</label>
            <div class="description">Enable detailed logging for debugging (only when needed)</div>
            <div class="checkbox-group">
                <input type="checkbox" id="debugLoggingEnabled" />
                <span>Enabled</span>
            </div>
        </div>
        
        <div class="buttons">
            <button class="btn-load" onclick="loadSettings()">🔄 Load</button>
            <button class="btn-save" onclick="saveSettings()">💾 Save</button>
        </div>
        
        <div class="data-dir">
            <strong>Data directory:</strong> <span id="dataDirDisplay">Loading...</span>
        </div>
    </div>
    
    <a href="heatmap.jsp" class="back-link">← Back to Heatmap</a>
</body>
</html>
