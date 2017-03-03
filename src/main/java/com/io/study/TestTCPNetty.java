package com.io.study;

import io.netty.bootstrap.ChannelFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ThreadFactory;

/**
 * @author: zouzhihui
 * @date: 2017-03-02 14-33
 */
public class TestTCPNetty {

    static {
        BasicConfigurator.configure();
    }

    public static void main(String[] args) {
        // 这就是主要的服务启动器
        ServerBootstrap serverBootstrap = new ServerBootstrap();

        // =============下面我们设置线程池
        // BOSS线程池
        EventLoopGroup bossLoopGroup = new NioEventLoopGroup(1);
        // WORK线程池：这样的申明方式，主要是为了向读者说明Netty的线程组是怎样工作
        ThreadFactory threadFactory = new DefaultThreadFactory("work thread pool");
        // CPU个数
        int processorsNumber = Runtime.getRuntime().availableProcessors();
        EventLoopGroup workLoopGroup = new NioEventLoopGroup(processorsNumber * 2, threadFactory, SelectorProvider.provider());
        // 指定Netty的Boss线程和work线程
        serverBootstrap.group(bossLoopGroup, workLoopGroup);

        // 如果是一下的申明方式，说明BOSS线程和WORK线程共享一个线程池
        // (实际上一般的情况环境下，这种共享线程池的方式已经够了)
        //serverBootstrap.group(workLoopGroup);


        // ===========下面我们设置服务的通道类型
        // 只能是实现了ServerChannel接口的“服务器”通道类
        serverBootstrap.channel(NioServerSocketChannel.class);
        // 当然也可以这样创建（那个SelectorProvider是不是感觉很熟悉）
        /*serverBootstrap.channelFactory(new ChannelFactory<NioServerSocketChannel>() {
            public NioServerSocketChannel newChannel() {
                return new NioServerSocketChannel(SelectorProvider.provider());
            }
        });*/


        // ===========设置处理器
        // 为了演示，这里我们设置了一组简单的ByteArrayDecoder和ByteArrayEncoder
        // Netty的特色就在这一连串“通道水管”中的“处理器”
        serverBootstrap.childHandler(new ChannelInitializer<NioSocketChannel>() {
            @Override
            protected void initChannel(NioSocketChannel ch) throws Exception {
                ch.pipeline().addLast(new ByteArrayEncoder());
                ch.pipeline().addLast(new TCPServerHandler());
                ch.pipeline().addLast(new ByteArrayDecoder());
            }
        });


        // ===========设置netty服务器绑定ip和端口
        serverBootstrap.option(ChannelOption.SO_BACKLOG, 128);
        serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        serverBootstrap.bind(new InetSocketAddress("0.0.0.0", 83));
        // 还可以监控多个端口
        //serverBootstrap.bind(new InetSocketAddress("0.0.0.0", 84));
    }

}


class TCPServerHandler extends ChannelInboundHandlerAdapter {
    private static Log LOGGER = LogFactory.getLog(TCPServerHandler.class);

    /**
     * 每一个channel，都有独立的handler、ChannelHandlerContext、ChannelPipeline、Attribute
     * 所以不需要担心多个channel中的这些对象相互影响
     * 这里我们使用content这个key，记录这个handler中已经接收到的客户端信息
     */
    private static AttributeKey<StringBuffer> content = AttributeKey.valueOf("content");

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("super.channelRegistered(ctx)");
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("super.channelUnregistered(ctx)");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("super.channelActive(ctx) = " + ctx.toString());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("super.channelInactive(ctx");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        LOGGER.info("channelRead(ChannelHandlerContext ctx, Object msg");
        /**
         * 我们使用IDE工具模拟长连接中的数据缓慢提交。
         * 由read方法负责接收数据，但只是进行数据累加，不进行任何处理
         */
        ByteBuf byteBuf = (ByteBuf) msg;
        try {
            StringBuffer contextBuffer = new StringBuffer();
            while(byteBuf.isReadable()) {
                contextBuffer.append((char) byteBuf.readByte());
            }

            // 加入临时区域
            StringBuffer content = ctx.attr(TCPServerHandler.content).get();
            if (null == content) {
                content = new StringBuffer();
                ctx.attr(TCPServerHandler.content).set(content);
            }
            content.append(contextBuffer);
        } catch (Exception e) {
            throw e;
        } finally {
            byteBuf.release();
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("super.channelReadComplete(ChannelHandlerContext ctx)");

        /**
         * 由readComplete方法负责检查数据是否接收完了。
         * 和之前的一样，我们检查整个内容中是否有“over”关键字
         */
        StringBuffer content = ctx.attr(TCPServerHandler.content).get();
        // 如果条件成立说明还没有接收到完整客户端信息
        if (content.indexOf("over") == -1) {
            return;
        }

        // 当接收到信息后，首先要做的是清空原来的历史信息
        ctx.attr(TCPServerHandler.content).set(new StringBuffer());

        // 准备向客户端发送响应
        ByteBuf byteBuf = ctx.alloc().buffer(1024);
        byteBuf.writeBytes("回发响应信息！".getBytes());
        ctx.writeAndFlush(byteBuf);

        /**
         * 关闭，正常终止这个通道上下文，就可以关闭通道了。
         * （如果不关闭，这个通道的回话将一直存在，只要网络是稳定的，服务器就可以随时通过这个回话向客户端发送信息）
         * 关闭通道意味着TCP将正常断开，其中所有的handler、ChannelHandlerContext、ChannelPipeline、Attribute等信息都将注销
         */
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        LOGGER.info("super.userEventTriggered(ctx, evt)");
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("super.channelWritabilityChanged(ctx)");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.info("super.exceptionCaught(ctx, cause)");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("super.handlerAdded(ctx)");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("super.handlerRemoved(ctx)");
    }
}
