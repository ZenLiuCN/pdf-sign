package signature;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Map;

/**
 * @author Zen.Liu
 * @since 2023-09-08
 */
public interface Util {

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

    static ByteBuf encode(File pdf,
                          File signature,
                          String signatureKeyword,
                          File seal,
                          String sealKeyword,
                          String date,
                          String dateKeyword,
                          String font,
                          Map<String, String> forms) throws IOException {
        ByteBuf pdfBuf, signBuf, sealBuf;
        try (var fileInputStream = new FileInputStream(pdf)) {
            var fileChannel = fileInputStream.getChannel();
            var mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, pdf.length());
            pdfBuf = Unpooled.wrappedBuffer(mappedByteBuffer);
        }
        try (var fileInputStream = new FileInputStream(signature)) {
            var fileChannel = fileInputStream.getChannel();
            var mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, signature.length());
            signBuf = Unpooled.wrappedBuffer(mappedByteBuffer);
        }
        var signKey = ByteBufAllocator.DEFAULT.buffer();
        signKey.writeCharSequence(signatureKeyword, StandardCharsets.UTF_8);
        try (var fileInputStream = new FileInputStream(seal)) {
            var fileChannel = fileInputStream.getChannel();
            var mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, seal.length());
            sealBuf = Unpooled.wrappedBuffer(mappedByteBuffer);
        }
        var sealKey = ByteBufAllocator.DEFAULT.buffer();
        sealKey.writeCharSequence(sealKeyword, StandardCharsets.UTF_8);
        var dateKey = ByteBufAllocator.DEFAULT.buffer();
        dateKey.writeCharSequence(dateKeyword, StandardCharsets.UTF_8);
        var dateFont = ByteBufAllocator.DEFAULT.buffer();
        if (font != null) dateFont.writeCharSequence(font, StandardCharsets.UTF_8);

        var dateBuf = ByteBufAllocator.DEFAULT.buffer();
        dateBuf.writeCharSequence(date, StandardCharsets.UTF_8);
        var full = ByteBufAllocator.DEFAULT.buffer();
        var form = ByteBufAllocator.DEFAULT.buffer();
        var fn = 0;
        if (forms != null && !forms.isEmpty()) {
            fn = forms.size();
            forms.forEach((k, v) -> {
                var key = ByteBufAllocator.DEFAULT.buffer();
                var nk = key.writeCharSequence(k, StandardCharsets.UTF_8);
                var val = ByteBufAllocator.DEFAULT.buffer();
                var nv = val.writeCharSequence(v, StandardCharsets.UTF_8);
                form
                        .writeIntLE(nk).writeBytes(key)
                        .writeIntLE(nv).writeBytes(val)
                ;
                key.release();
                val.release();
            });
        }
        full
                .writeIntLE(pdfBuf.readableBytes()).writeBytes(pdfBuf)
                .writeIntLE(signBuf.readableBytes()).writeBytes(signBuf)
                .writeIntLE(signKey.readableBytes()).writeBytes(signKey)
                .writeIntLE(sealBuf.readableBytes()).writeBytes(sealBuf)
                .writeIntLE(sealKey.readableBytes()).writeBytes(sealKey)
                .writeIntLE(dateBuf.readableBytes()).writeBytes(dateBuf)
                .writeIntLE(dateKey.readableBytes()).writeBytes(dateKey)
                .writeIntLE(dateFont.readableBytes()).writeBytes(dateFont)
                .writeIntLE(fn).writeBytes(form)
        ;
        sealKey.release();
        sealBuf.release();
        dateKey.release();
        dateBuf.release();
        signKey.release();
        signBuf.release();
        pdfBuf.release();
        form.release();
        return full;
    }
}
