package br.com.ccs.rinha.api.handler;

import br.com.ccs.rinha.api.model.input.PaymentRequest;
import br.com.ccs.rinha.config.ExecutorConfig;
import br.com.ccs.rinha.repository.JdbcPaymentRepository;
import br.com.ccs.rinha.service.PaymentProcessorClient;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;


public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = Logger.getLogger(HttpServerHandler.class.getSimpleName());
    private final String postPaymentURI = "/payments";
    private final String getSummaryURI = "/payments-summary";
    private final String postPurgePaymentsURI = "/purge-payments";

    private final PaymentProcessorClient paymentProcessorClient = PaymentProcessorClient.getInstance();
    private final JdbcPaymentRepository paymentRepository = JdbcPaymentRepository.getInstance();
    private final ThreadPoolExecutor executor = ExecutorConfig.getExecutor();

    private static HttpServerHandler instance;

    private HttpServerHandler() {
    }

    public static HttpServerHandler getInstance() {
        if (instance == null) {
            HttpServerHandler.instance = new HttpServerHandler();
        }
        return instance;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {

        String uri = request.uri();

        try {
            if (uri.equals(postPaymentURI)) {
                doPayment(ctx, request);
            }

            if (uri.startsWith(getSummaryURI)) {
                doSummary(ctx, request);
            }

            if (uri.equals(postPurgePaymentsURI)) {
                doPurge(ctx);
            }

            ctx.close();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao processar requisição: " + uri, e);
        }
    }

    private void doPayment(ChannelHandlerContext ctx, FullHttpRequest request) {
        executor.submit(() -> {
            try {
                paymentProcessorClient.processPayment(PaymentRequest.parse(request.content().toString(CharsetUtil.UTF_8)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, executor);
        sendResponse(ctx);
    }

    private void doSummary(ChannelHandlerContext ctx, FullHttpRequest request) {

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        Map<String, List<String>> queryParameters = decoder.parameters();

        // 2. Acessar os parâmetros "from" e "to"
        List<String> fromList = queryParameters.get("from");
        List<String> toList = queryParameters.get("to");

        var from = OffsetDateTime.parse(fromList.getFirst());
        var to = OffsetDateTime.parse(toList.getFirst());
        sendJsonResponse(ctx, paymentRepository.getSummary(from, to).toJson());
    }

    private void doPurge(ChannelHandlerContext ctx) {
        paymentRepository.purge();
        sendResponse(ctx);
    }


    private void sendJsonResponse(ChannelHandlerContext ctx, String jsonContent) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(jsonContent, CharsetUtil.UTF_8)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }


    private void sendResponse(ChannelHandlerContext ctx) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        ctx.writeAndFlush(response);
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.log(Level.SEVERE, "Erro inesperado no Netty handler:", cause);
        ctx.close(); // Fecha a conexão em caso de erro
    }
}