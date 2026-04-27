$PYTHON = "C:\Users\Soormayee\AppData\Local\Programs\Python\Python313\python.exe"
Write-Output "Building search index (max 1000 images)..."
& $PYTHON build_index.py --max-images 1000
Write-Output "Starting visual search API..."
& $PYTHON visual_search_api.py
