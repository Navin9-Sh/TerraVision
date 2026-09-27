const imageInput = document.getElementById('imageInput');
const uploadBox = document.getElementById('uploadBox');
const uploadPlaceholder = document.getElementById('uploadPlaceholder');
const previewImage = document.getElementById('previewImage');
const classifyBtn = document.getElementById('classifyBtn');
const resultsCard = document.getElementById('resultsCard');
const lowConfidenceBanner = document.getElementById('lowConfidenceBanner');
const classifyError = document.getElementById('classifyError');

document.getElementById('uploadIcon').innerHTML = ICONS.upload;

uploadBox.addEventListener('click', () => imageInput.click());

uploadBox.addEventListener('dragover', e => {
    e.preventDefault();
    uploadBox.classList.add('drag-over');
});
uploadBox.addEventListener('dragleave', () => {
    uploadBox.classList.remove('drag-over');
});
uploadBox.addEventListener('drop', e => {
    e.preventDefault();
    uploadBox.classList.remove('drag-over');
    const file = e.dataTransfer.files[0];
    if (file) handleFile(file);
});

imageInput.addEventListener('change', () => {
    if (imageInput.files[0]) handleFile(imageInput.files[0]);
});

function handleFile(file) {
    const reader = new FileReader();
    reader.onload = e => {
        previewImage.src = e.target.result;
        previewImage.style.display = 'block';
        uploadPlaceholder.style.display = 'none';
        classifyBtn.disabled = false;
        resultsCard.style.display = 'none';
        classifyError.style.display = 'none';
    };
    reader.readAsDataURL(file);
}

classifyBtn.addEventListener('click', async () => {
    const file = imageInput.files[0];
    if (!file) return;

    classifyError.style.display = 'none';
    classifyBtn.textContent = 'Classifying...';
    classifyBtn.disabled = true;

    const formData = new FormData();
    formData.append('image', file);

    try {
        const result = await apiFetch('/predict', { method: 'POST', body: formData });
        showResults(result);
    } catch (err) {
        classifyError.innerHTML = `<span style="width:18px;height:18px;display:inline-flex;">${ICONS.xCircle}</span><span>${err.message}</span>`;
        classifyError.style.display = 'flex';
    }

    classifyBtn.textContent = 'Classify image';
    classifyBtn.disabled = false;
});

function showResults(result) {
    const top = result.top3[0];

    resultsCard.classList.toggle('low-confidence', result.lowConfidence);

    if (result.lowConfidence) {
        lowConfidenceBanner.innerHTML = `<span style="width:18px;height:18px;display:inline-flex;">${ICONS.alertTriangle}</span><span>${result.warningMessage}</span>`;
        lowConfidenceBanner.style.display = 'flex';
    } else {
        lowConfidenceBanner.style.display = 'none';
    }

    document.getElementById('resultClass').textContent = top.className;
    document.getElementById('resultDesc').textContent = top.description;

    const barsHtml = result.top3.map(r => `
        <div class="bar-row">
            <span class="bar-label">${r.className}</span>
            <div class="bar-track">
                <div class="bar-fill" style="width:${r.confidencePercent}%"></div>
            </div>
            <span class="bar-pct">${r.confidencePercent}%</span>
        </div>
    `).join('');

    document.getElementById('confidenceBars').innerHTML = barsHtml;
    document.getElementById('inferenceTimeNote').textContent =
        `Inference took ${result.inferenceTimeMs} ms - EuroSAT Sentinel-2 model (10 classes)`;

    resultsCard.style.display = 'block';
    resultsCard.scrollIntoView({ behavior: 'smooth' });
}
