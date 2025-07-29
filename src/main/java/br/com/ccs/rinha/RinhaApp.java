package br.com.ccs.rinha;

import br.com.ccs.rinha.api.handler.HttpServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import java.util.logging.Logger;

public class RinhaApp {

    private static final Logger logger = Logger.getLogger(RinhaApp.class.getSimpleName());

    private static final int PORT = Integer.parseInt(System.getenv("SERVER_PORT"));

    public static void main(String[] args) throws Exception {


        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class) // Usar canal NIO para I/O não bloqueante
                    // Se você estiver em Linux e quiser usar epoll para maior performance:
                    // .channel(EpollServerSocketChannel.class) // Requer netty-transport-native-epoll
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            // Adiciona os codecs HTTP do Netty no pipeline
                            ch.pipeline()
                                    .addLast(new HttpRequestDecoder()) // Decodifica requisições HTTP (cabeçalhos, corpo)
                                    .addLast(new HttpObjectAggregator(1024)) // Agrega partes HTTP em uma FullHttpRequest
                                    .addLast(new HttpResponseEncoder()) // Codifica respostas HTTP
                                    .addLast(new HttpServerHandler()); // Seu handler customizado para a lógica de negócio
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128) // Número máximo de conexões pendentes no backlog
                    .childOption(ChannelOption.SO_KEEPALIVE, true); // Manter conexões ativas

            logger.info("Iniciando servidor Netty na porta: " + PORT);

            // Vincula a porta e inicia o servidor.
            ChannelFuture f = b.bind(PORT).sync(); // Bloqueia até o servidor ser iniciado

            // Espera até que o socket do servidor seja fechado.
            f.channel().closeFuture().sync(); // Bloqueia até o servidor ser desligado

        } finally {
            // Desliga todos os event loops para terminar todas as threads.
            logger.info("Desligando servidor Netty.");
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}