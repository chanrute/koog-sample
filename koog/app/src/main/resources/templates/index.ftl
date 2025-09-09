<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title}</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 2rem;
            background-color: #f5f5f5;
        }
        .container {
            background-color: white;
            padding: 2rem;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            text-align: center;
        }
        h1 {
            color: #333;
            margin-bottom: 1rem;
        }
        .subtitle {
            color: #666;
            margin-bottom: 2rem;
        }
        .analysis-form {
            background-color: #f8f9fa;
            padding: 2rem;
            border-radius: 8px;
            margin: 2rem 0;
            border: 2px solid #007bff;
        }
        .analysis-form h2 {
            color: #007bff;
            margin-top: 0;
        }
        .form-group {
            margin-bottom: 1rem;
            text-align: left;
        }
        .form-group label {
            display: block;
            margin-bottom: 0.5rem;
            font-weight: bold;
            color: #333;
        }
        .form-group input {
            width: 100%;
            padding: 0.75rem;
            border: 1px solid #ddd;
            border-radius: 4px;
            font-size: 1rem;
            box-sizing: border-box;
        }
        .form-group input:focus {
            outline: none;
            border-color: #007bff;
            box-shadow: 0 0 0 2px rgba(0,123,255,0.25);
        }
        #analyzeBtn {
            background-color: #007bff;
            color: white;
            padding: 0.75rem 2rem;
            border: none;
            border-radius: 4px;
            font-size: 1rem;
            cursor: pointer;
            transition: background-color 0.2s;
        }
        #analyzeBtn:hover {
            background-color: #0056b3;
        }
        #analyzeBtn:disabled {
            background-color: #6c757d;
            cursor: not-allowed;
        }
        .loading-spinner {
            border: 4px solid #f3f3f3;
            border-top: 4px solid #007bff;
            border-radius: 50%;
            width: 40px;
            height: 40px;
            animation: spin 2s linear infinite;
            margin: 0 auto 1rem;
        }
        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
        #loadingState {
            text-align: center;
            padding: 2rem;
            color: #666;
        }
        #resultArea {
            background-color: white;
            padding: 1.5rem;
            border-radius: 6px;
            border: 1px solid #ddd;
            margin-top: 1rem;
        }
        .result-success {
            border-left: 4px solid #28a745;
            background-color: #d4edda;
        }
        .result-error {
            border-left: 4px solid #dc3545;
            background-color: #f8d7da;
        }
        .recipe-info {
            text-align: left;
        }
        .recipe-info h4 {
            color: #007bff;
            margin-bottom: 0.5rem;
        }
        .recipe-info p {
            margin: 0.5rem 0;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>${title}</h1>

        <!-- PDF解析フォーム -->
        <div class="analysis-form">
            <h2>📄 PDF レシピ解析</h2>
            <p>PDFのURLを入力して、AIがレシピ情報を抽出・分析します</p>
            
            <form id="analysisForm">
                <div class="form-group">
                    <label for="pdfUrl">PDF URL:</label>
                    <input type="url" id="pdfUrl" name="pdfUrl" placeholder="https://example.com/recipe.pdf" required>
                </div>
                <button type="submit" id="analyzeBtn">解析開始</button>
            </form>
            
            <!-- ローディング表示 -->
            <div id="loadingState" style="display: none;">
                <div class="loading-spinner"></div>
                <p>AI解析中... しばらくお待ちください</p>
            </div>
            
            <!-- 結果表示エリア -->
            <div id="resultArea" style="display: none;">
                <h3>解析結果</h3>
                <div id="resultContent"></div>
            </div>
        </div>
    </div>

    <script>
        document.getElementById('analysisForm').addEventListener('submit', async function(e) {
            e.preventDefault();
            
            const pdfUrl = document.getElementById('pdfUrl').value;
            const analyzeBtn = document.getElementById('analyzeBtn');
            const loadingState = document.getElementById('loadingState');
            const resultArea = document.getElementById('resultArea');
            const resultContent = document.getElementById('resultContent');
            
            // フォームの状態をリセット
            analyzeBtn.disabled = true;
            analyzeBtn.textContent = '解析中...';
            loadingState.style.display = 'block';
            resultArea.style.display = 'none';
            
            try {
                const response = await fetch('/analyze', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({ pdfUrl: pdfUrl })
                });
                
                const result = await response.json();
                
                // 結果を表示
                if (response.ok) {
                    resultContent.innerHTML = formatResult(result);
                    resultArea.className = 'result-success';
                } else {
                    resultContent.innerHTML = `<p><strong>エラー:</strong> ${'$'}{result.error || '解析に失敗しました'}</p>`;
                    resultArea.className = 'result-error';
                }
                
                resultArea.style.display = 'block';
                
            } catch (error) {
                console.error('解析エラー:', error);
                resultContent.innerHTML = '<p><strong>エラー:</strong> ネットワークエラーが発生しました。</p>';
                resultArea.className = 'result-error';
                resultArea.style.display = 'block';
            } finally {
                // フォームの状態を元に戻す
                analyzeBtn.disabled = false;
                analyzeBtn.textContent = '解析開始';
                loadingState.style.display = 'none';
            }
        });
        
        function formatResult(result) {
            // aiAgentから直接PdfValidationResultが返される
            if (!result.isRecipe) {
                return '<div class="recipe-info">' +
                    '<h4>📋 判定結果</h4>' +
                    '<p><strong>レシピ判定:</strong> ❌ レシピではありません</p>' +
                    '<p><strong>理由:</strong> ' + result.reason + '</p>' +
                    '</div>';
            }
            
            return '<div class="recipe-info">' +
                '<h4>📋 判定結果</h4>' +
                '<p><strong>レシピ判定:</strong> ✅ レシピです</p>' +
                '<p><strong>理由:</strong> ' + result.reason + '</p>' +
                '</div>';
        }
    </script>
</body>
</html>