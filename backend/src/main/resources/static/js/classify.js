const imageInput = document.getElementById('imageInput');
const uploadBox = document.getElementById('uploadBox');
const uploadPlaceholder = document.getElementById('uploadPlaceholder');
const previewImage = document.getElementById('previewImage');
const classifyBtn = document.getElementById('classifyBtn');
const resultsCard = document.getElementById('resultsCard');
const lowConfidenceBanner = document.getElementById('lowConfidenceBanner');
const classifyError = document.getElementById('classifyError');

const uploadAnotherBtn = document.getElementById('uploadAnotherBtn');

document.getElementById('uploadIcon').innerHTML = ICONS.upload;
document.getElementById('uploadAnotherIcon').innerHTML = ICONS.refresh;

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

uploadAnotherBtn.addEventListener('click', resetUpload);

/** Back to the empty upload state, without reloading the page. */
function resetUpload() {
    imageInput.value = '';
    previewImage.removeAttribute('src');
    previewImage.style.display = 'none';
    uploadPlaceholder.style.display = '';
    classifyBtn.disabled = true;
    classifyBtn.textContent = 'Classify image';
    resultsCard.style.display = 'none';
    lowConfidenceBanner.style.display = 'none';
    classifyError.style.display = 'none';
    uploadBox.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function showResults(result) {
    resultsCard.classList.toggle('low-confidence', result.lowConfidence);

    if (result.lowConfidence) {
        lowConfidenceBanner.innerHTML = `<span style="width:18px;height:18px;display:inline-flex;">${ICONS.alertTriangle}</span><span>${result.warningMessage}</span>`;
        lowConfidenceBanner.style.display = 'flex';
    } else {
        lowConfidenceBanner.style.display = 'none';
    }

    document.getElementById('resultClass').textContent = result.className;
    document.getElementById('resultDesc').textContent = result.description;
    document.getElementById('resultPct').textContent = formatPercent(result.confidencePercent);
    document.getElementById('resultBar').style.width = `${result.confidencePercent}%`;
    document.getElementById('inferenceTimeNote').textContent =
        `Inference took ${result.inferenceTimeMs} ms - EuroSAT Sentinel-2 model (10 classes)`;

    resultsCard.style.display = 'block';
    resultsCard.scrollIntoView({ behavior: 'smooth' });
}
