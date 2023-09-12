package signature;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import reactor.netty.http.server.HttpServer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;

/**
 * @author Zen.Liu
 * @since 2023-09-08
 */
public interface Application {
    static void main(String[] args) {
        var serv = new ITextSign();
        var http = HttpServer.create()
                .port(Optional.of(System.getProperty("sign.port")).map(Integer::parseInt).orElse(8080))
                .route(routes -> routes.post("/sign", (q, r) ->
                                r.send(q.receive().aggregate()
                                        .doOnDiscard(ByteBuf.class, ReferenceCountUtil::release)
                                        .flatMap(buf -> {
                                            var file = buf.readSlice(buf.readIntLE());
                                            var sign = buf.readSlice(buf.readIntLE());
                                            var signKey = buf.readSlice(buf.readIntLE()).toString(StandardCharsets.UTF_8);

                                            var seal = buf.readSlice(buf.readIntLE());
                                            var sealKey = buf.readSlice(buf.readIntLE()).toString(StandardCharsets.UTF_8);

                                            var date = buf.readSlice(buf.readIntLE()).toString(StandardCharsets.UTF_8);
                                            var dateKey = buf.readSlice(buf.readIntLE()).toString(StandardCharsets.UTF_8);
                                            var form = new HashMap<String, String>();
                                            {
                                                var n = buf.readIntLE();
                                                if (n == 0) {
                                                    form = null;
                                                } else {
                                                    for (int i = 0; i < n; i++) {
                                                        form.put(buf.readSlice(buf.readIntLE()).toString(StandardCharsets.UTF_8), buf.readSlice(buf.readIntLE()).toString(StandardCharsets.UTF_8));
                                                    }
                                                }
                                            }

                                            return serv.sign(file, sign, signKey, seal, sealKey, date, dateKey, form);
                                        }))

                        )
                );
        http.warmup().block();
        http.bindNow()
                .onDispose()
                .block();
    }
}
