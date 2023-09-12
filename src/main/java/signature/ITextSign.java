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
import com.itextpdf.layout.borders.RidgeBorder;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import io.netty.buffer.*;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            String keyword
    ) implements Operate {
        @Override
        public void process(PdfDocument doc, PdfPage page, Canvas canvas) throws Exception {
            var rect = findLastKeyword(doc, doc.getPageNumber(page), keyword).orElseThrow(() -> new IllegalStateException("not found keyword at last page: " + keyword));
            if (log.isDebugEnabled()) {
                log.info("rect for {} {}", this, dumpRect(rect));
            }
            var data = ImageDataFactory.create(ByteBufUtil.getBytes(this.image));
            data.setInverted(true);
            var image = new Image(data);
            image.setFixedPosition(rect.getRight(), rect.getTop() - image.getImageHeight(), image.getWidth());
            canvas.add(image);
            this.image.release();
        }
    }


    record TextKeyword(
            String text,
            String keyword,
            String font,
            float size
    ) implements Operate {
        @Override
        public void process(PdfDocument doc, PdfPage page, Canvas canvas) throws Exception {
            var rect = findLastKeyword(doc, doc.getPageNumber(page), keyword).orElseThrow(() -> new IllegalStateException("not found keyword at last page: " + keyword));
            if (log.isDebugEnabled()) {
                log.info("rect for {} {}", this, dumpRect(rect));
            }
            var font = PdfFontFactory.createFont(this.font, "UniGB-UCS2-H", doc);
            canvas.setBorder(new RidgeBorder(5));
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

/*    record TextLocate(
            ByteBuf image,
            float x,
            float y
    ) implements Operate {
        @Override
        public void process(int maxPage, PdfReader reader, PdfStamper stamper) throws Exception {
            var image = Image.getInstance(ByteBufUtil.getBytes(this.image));
            var content = stamper.getOverContent(maxPage);
            content.addImage(image, image.getScaledWidth(), 0, 0, image.getScaledHeight(), x, y);
        }
    }*/

    interface Operate {
        void process(PdfDocument doc, PdfPage page, Canvas canvas) throws Exception;

    }

    public Mono<ByteBuf> sign(ByteBuf file, List<Operate> operates) {
        var out = ByteBufAllocator.DEFAULT.buffer();
        try (var os = new ByteBufOutputStream(out)) {
            var reader = new PdfReader(new ByteBufInputStream(file));
            var writer = new PdfWriter(os);
            try (var doc = new PdfDocument(reader, writer)) {
                var lastPage = doc.getLastPage();
                try (var canvas = new Canvas(lastPage, new Rectangle(0, 0, lastPage.getPageSize().getWidth(), lastPage.getPageSize().getHeight()))) {
                    for (var op : operates) {
                        op.process(doc, lastPage, canvas);
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
        if (sign != null && signKeyword != null) {
            ops.add(new ImageKeyword(sign, signKeyword));
        }
        if (seal != null && sealKeyword != null) {
            ops.add(new ImageKeyword(seal, sealKeyword));
        }
        if (date != null && dateKeyword != null) {
            ops.add(new TextKeyword(date, dateKeyword, "STSong-Light", 12));
        }
        try {
            return sign(file, ops);
        } finally {
            file.release();
        }
    }
}
