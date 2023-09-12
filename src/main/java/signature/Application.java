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
                                            var file = buf.readSlice(buf.readIntLE()).retain();
                                            var sn = buf.readIntLE();
                                            var sign = sn > 0 ? buf.readSlice(sn).retain() : null;
                                            var snk = buf.readIntLE();
                                            var signKey = snk > 0 ? buf.readSlice(snk).toString(StandardCharsets.UTF_8) : null;
                                            var sen = buf.readIntLE();
                                            var seal = sen > 0 ? buf.readSlice(sen).retain() : null;
                                            var sekn = buf.readIntLE();
                                            var sealKey = sekn > 0 ? buf.readSlice(sekn).toString(StandardCharsets.UTF_8) : null;
                                            var dn = buf.readIntLE();
                                            var date = dn > 0 ? buf.readSlice(dn).toString(StandardCharsets.UTF_8) : null;
                                            var dkn=buf.readIntLE();
                                            var dateKey = dkn>0? buf.readSlice(dkn).toString(StandardCharsets.UTF_8):null;
                                            var form = new HashMap<String, String>();
                                            {
                                                var n = buf.readIntLE();
                                                if (n <= 0) {
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
