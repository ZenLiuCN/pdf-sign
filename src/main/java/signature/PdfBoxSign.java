package signature;

import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Mono;

/**
 * @author Zen.Liu
 * @since 2023-09-08
 */
public class PdfBoxSign implements SignatureService {
    @Override
    public Mono<ByteBuf> sign(ByteBuf file, ByteBuf sign, String signKeyword, ByteBuf seal, String sealKeyword, String date, String dateKeyword) {
        return null;
    }
}
