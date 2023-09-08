package signature;


import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;


/**
 * @author Zen.Liu
 * @since 2023-09-08
 */
public interface SignatureService {
    Logger log = LoggerFactory.getLogger(SignatureService.class);


    Mono<ByteBuf> sign(ByteBuf file, ByteBuf sign, String signKeyword, ByteBuf seal, String sealKeyword, String date, String dateKeyword);


}
