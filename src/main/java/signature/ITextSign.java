package signature;

import com.itextpdf.forms.fields.PdfFormCreator;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.parser.PdfDocumentContentParser;
import com.itextpdf.kernel.pdf.canvas.parser.listener.RegexBasedLocationExtractionStrategy;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import io.netty.buffer.*;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Zen.Liu
 * @since 2023-09-08
 */
public class ITextSign implements SignatureService {
    record Form(
            Map<String, String> values
    ) implements Operate {

        @Override
        public void process(PdfDocument doc, PdfPage page, Canvas canvas) throws Exception {
            var form = PdfFormCreator.getAcroForm(doc, false);
            if (form == null) return;
            form.getAllFormFields().forEach((name, field) -> {
                var val = values.get(name);
                if (val != null) {
                    field.setValue(val)
                            .setReadOnly(true);
                }
            });

        }
    }

    record ImageKeyword(
            ByteBuf image,
            String keyword,
            int offset,
            float opacity
    ) implements Keyword {
        @Override
        public void process(PdfDocument doc, PdfPage page, Rectangle rect, Canvas canvas) throws Exception {
            if (log.isDebugEnabled()) {
                log.info("rect for {} {}", this, dumpRect(rect));
            }
            var data = ImageDataFactory.create(ByteBufUtil.getBytes(this.image));
            data.setInverted(true);
            var image = new Image(data);
            image.setFixedPosition(rect.getRight(), rect.getTop() - image.getImageHeight() + offset, image.getWidth());
            if (opacity > 0 && opacity < 1) {
                image.setOpacity(opacity);
            }
            canvas.add(image);
            this.image.release();
        }
    }


    record TextKeyword(
            String text,
            String keyword,
            String font,
            float size
    ) implements Keyword {
        @Override
        public void process(PdfDocument doc, PdfPage page, Rectangle rect, Canvas canvas) throws Exception {
            if (log.isDebugEnabled()) {
                log.info("rect for {} {}", this, dumpRect(rect));
            }
            var font = PdfFontFactory.createRegisteredFont(this.font);
            canvas.add(new Paragraph(this.text)
                    .setFixedPosition(rect.getRight(), rect.getBottom(), page.getPageSize().getWidth())
                    .setFont(font)
                    .setFontSize(this.size));

        }
    }

    static String dumpRect(Rectangle rect) {
        return ("x:" + rect.getX() + ",y:" + rect.getY() + ",w:" + rect.getWidth() + ",h:" + rect.getHeight());
    }

    static Optional<Rectangle> findLastKeyword(PdfDocument document, int startPage, String keyword) {
        var strategy = new PdfDocumentContentParser(document).processContent(startPage, new RegexBasedLocationExtractionStrategy(keyword));
        var location = new ArrayList<>(strategy.getResultantLocations());
        if (location.isEmpty()) return Optional.empty();
        return Optional.of(location.get(location.size() - 1).getRectangle());
    }


    public ITextSign() {
        PdfFontFactory.registerSystemDirectories();
        var f = Paths.get("./fonts").toAbsolutePath();
        if (f.toFile().exists()) {
            PdfFontFactory.registerDirectory(f.toString());
            log.info("fonts: {}", PdfFontFactory.getRegisteredFonts());
        }
    }

    interface Operate {
        void process(PdfDocument doc, PdfPage page, Canvas canvas) throws Exception;

    }

    interface Keyword extends Operate {
        String keyword();

        default void process(PdfDocument doc, PdfPage page, Canvas canvas) throws Exception {
            throw new IllegalArgumentException();
        }

        void process(PdfDocument doc, PdfPage page, Rectangle rect, Canvas canvas) throws Exception;
    }

    public Mono<ByteBuf> sign(ByteBuf file, List<Operate> operates) {
        var out = ByteBufAllocator.DEFAULT.buffer();
        try (var os = new ByteBufOutputStream(out)) {
            var reader = new PdfReader(new ByteBufInputStream(file));
            var writer = new PdfWriter(os);
            try (var doc = new PdfDocument(reader, writer)) {
                var lastPage = doc.getLastPage();
                var keywords = operates.stream()
                        .filter(x -> x instanceof Keyword)
                        .map(x -> {
                            var kw = ((Keyword) x).keyword();
                            var rect = findLastKeyword(doc, doc.getPageNumber(lastPage), kw).orElseThrow(() -> new IllegalStateException("not found keyword " + kw + " at last page"));
                            return new AbstractMap.SimpleImmutableEntry<>(x, rect);
                        })
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


                try (var canvas = new Canvas(lastPage, new Rectangle(0, 0, lastPage.getPageSize().getWidth(), lastPage.getPageSize().getHeight()))) {
                    for (var op : operates) {
                        if (keywords.containsKey(op)) {
                            ((Keyword) op).process(doc, lastPage, keywords.get(op), canvas);
                        } else {
                            op.process(doc, lastPage, canvas);
                        }

                    }
                }

            }
        } catch (Exception e) {
            return Mono.error(e);
        }
        return Mono.just(out);
    }

    @Override
    public Mono<ByteBuf> sign(ByteBuf file, @Nullable ByteBuf sign, @Nullable String signKeyword, @Nullable ByteBuf seal, @Nullable String sealKeyword, @Nullable String date, @Nullable String dateKeyword, @Nullable Map<String, String> forms) {
        var ops = new ArrayList<Operate>();

        if (forms != null && !forms.isEmpty()) {
            ops.add(new Form(forms));
        }
        if (date != null && dateKeyword != null) {
            ops.add(new TextKeyword(date, dateKeyword, "STSONG", 12));
        }

        if (sign != null && signKeyword != null) {
            ops.add(new ImageKeyword(sign, signKeyword, 0, 1));
        }
        if (seal != null && sealKeyword != null) {
            ops.add(new ImageKeyword(seal, sealKeyword, 30, 0.9f));
        }

        try {
            return sign(file, ops);
        } finally {
            file.release();
        }
    }
}
