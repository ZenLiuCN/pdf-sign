package signature;


import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.parser.PdfDocumentContentParser;
import com.itextpdf.kernel.pdf.canvas.parser.listener.RegexBasedLocationExtractionStrategy;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.borders.RidgeBorder;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import io.netty.buffer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * @author Zen.Liu
 * @since 2023-09-08
 */
public interface SignatureService {
    Logger log = LoggerFactory.getLogger(SignatureService.class);

    record ImageKeyword(
            ByteBuf image,
            String keyword
    ) implements Operate {
        @Override
        public void process(int maxPage, PdfDocument doc) throws Exception {
            var content = doc.getPage(maxPage);
            var rect = findLastKeyword(doc, maxPage, keyword).orElseThrow(() -> new IllegalStateException("not found keyword at last page: " + keyword));
            if (log.isDebugEnabled()) {
                log.info("rect for {} {}",this, dumpRect(rect));
            }
            var x = rect.getRight();
            var y = rect.getTop();
            var data = ImageDataFactory.create(ByteBufUtil.getBytes(this.image));
            data.setHeight(100);
            data.setWidth(200);
            var image = new Image(data);
            var w = image.getImageScaledWidth();
            var h = image.getImageScaledHeight();
            try (var canvas = new Canvas(content, new Rectangle(x, y, w, h))) {
                canvas.add(image);
            }
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

    record TextKeyword(
            String text,
            String keyword,
            String font,
            float size
    ) implements Operate {
        @Override
        public void process(int maxPage, PdfDocument doc) throws Exception {
            var content = doc.getPage(maxPage);
            var rect = findLastKeyword(doc, maxPage, keyword).orElseThrow(() -> new IllegalStateException("not found keyword at last page: " + keyword));
            if (log.isDebugEnabled()) {
                log.info("rect for {} {}",this, dumpRect(rect));
            }
            var x = rect.getRight();
            var y = content.getPageSize().getHeight() - rect.getTop();
            var font = PdfFontFactory.createFont(this.font, "UniGB-UCS2-H", doc);
            var w = content.getPageSize().getWidth();
            var h = content.getPageSize().getHeight() - y;
            try (var canvas = new Canvas(content, new Rectangle(x, y, w, h))) {
                canvas.setBorder(new RidgeBorder(5));
                canvas.add(new Paragraph(this.text).setFont(font).setFontSize(this.size));
            }

        }
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
        void process(int maxPage, PdfDocument document) throws Exception;

    }

    default Mono<ByteBuf> sign(ByteBuf file, List<Operate> operates) {
        var out = ByteBufAllocator.DEFAULT.buffer();
        try (var os = new ByteBufOutputStream(out)) {
            var reader = new PdfReader(new ByteBufInputStream(file));
            var writer = new PdfWriter(os);
            try (var doc = new PdfDocument(reader, writer)) {
                for (var op : operates) {
                    op.process(doc.getNumberOfPages(), doc);
                }
            }
        } catch (Exception e) {
            return Mono.error(e);
        }
        return Mono.just(out);
    }


    static ByteBuf readFile(String path) throws IOException {
        var file = Paths.get(path).toFile();
        try (var fileInputStream = new FileInputStream(file)) {
            var fileChannel = fileInputStream.getChannel();
            var mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, file.length());
            return Unpooled.wrappedBuffer(mappedByteBuffer);
        }
    }

    static void writeFile(String path, ByteBuf buf, boolean override) throws IOException {
        var file = Paths.get(path).toFile();
        if (file.exists() && !override) throw new IllegalStateException("file already exists");
        file.createNewFile();
        try (var fos = new FileOutputStream(file)) {
            var fileChannel = fos.getChannel();
            fileChannel.write(buf.nioBuffer());
        }
    }

    public static void main(String[] args) throws IOException {
        var key = "甲方（签字）:";
        var service = new SignatureService() {
        };
        var pdf = readFile("test.pdf");
        var img = readFile("sign.png");
        var out = service.sign(pdf, List.of(
                        new ImageKeyword(img, key),
                        // new ImageLocate(img, 0, 0),
                        new TextKeyword("2023年9月8日", "双方签订日期：", "STSong-Light", 12)
                ))
                .block();
        writeFile("output.pdf", out, true);
    }
}
