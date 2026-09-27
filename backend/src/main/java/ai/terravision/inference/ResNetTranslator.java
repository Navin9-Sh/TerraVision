package ai.terravision.inference;

import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.util.NDImageUtils;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Mirrors the training-time transform (resize -> tensor -> ImageNet normalize), except
 * the resize step is an aspect-preserving letterbox (see {@link ImagePreprocessor}) rather
 * than the original hard center-crop.
 */
public class ResNetTranslator implements Translator<Image, Classifications> {

    private static final int INPUT_SIZE = 224;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    private final List<String> classNames;

    public ResNetTranslator(List<String> classNames) {
        this.classNames = classNames;
    }

    @Override
    public NDList processInput(TranslatorContext ctx, Image input) {
        BufferedImage buffered = (BufferedImage) input.getWrappedImage();
        BufferedImage letterboxed = ImagePreprocessor.resizeWithPadding(buffered, INPUT_SIZE);
        Image djlImage = ImageFactory.getInstance().fromImage(letterboxed);

        NDArray array = djlImage.toNDArray(ctx.getNDManager(), Image.Flag.COLOR); // HWC, uint8
        array = NDImageUtils.toTensor(array); // -> CHW, float32 in [0, 1]
        array = normalize(array);
        return new NDList(array);
    }

    private NDArray normalize(NDArray array) {
        NDArray mean = array.getManager().create(MEAN).reshape(3, 1, 1);
        NDArray std = array.getManager().create(STD).reshape(3, 1, 1);
        return array.sub(mean).div(std);
    }

    @Override
    public Classifications processOutput(TranslatorContext ctx, NDList list) {
        NDArray probabilities = list.singletonOrThrow().softmax(0);
        return new Classifications(classNames, probabilities);
    }

    @Override
    public Batchifier getBatchifier() {
        return Batchifier.STACK;
    }
}
