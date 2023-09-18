package signature;


import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

import java.util.List;
import java.util.Map;


/**
 * @author Zen.Liu
 * @since 2023-09-08
 */
public interface SignatureService {
    Logger log = LoggerFactory.getLogger(SignatureService.class);


    Mono<ByteBuf> sign(ByteBuf file,
                       @Nullable ByteBuf sign,
                       @Nullable String signKeyword,
                       @Nullable ByteBuf seal,
                       @Nullable String sealKeyword,
                       @Nullable String date,
                       @Nullable String dateKeyword,
                       @Nullable String font,
                       @Nullable Map<String, String> forms);

    List<String> fonts();
}
