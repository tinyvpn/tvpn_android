#include <jni.h>
#include <string>
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/tcp.h>
#include <sys/ioctl.h>
#include <arpa/inet.h>
#include <thread>

#include "sysutl.h"
#include "sockutl.h"
#include "fileutl.h"
#include "timeutl.h"
#include "stringutl.h"
#include "obfuscation_utl.h"
#include "vpn_packet.h"
#include "sockhttp.h"
#include "ssl_client2.h"
#include "http_client.h"

static int g_protocol;
static int client_sock;
static int g_fd_tun_dev;
static int g_in_recv_tun;
static int g_in_recv_socket;
static int g_isRun;
static uint32_t g_private_ip;
static int in_traffic, out_traffic;

extern "C" JNIEXPORT jstring JNICALL
Java_com_tinyvpn_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
extern "C" JNIEXPORT jint JNICALL
Java_com_tinyvpn_MainActivity_initFromJNI(
        JNIEnv *env,
        jobject /* this */, jstring log_path) {
    string_utl::set_random_http_domains();
    sock_http::init_http_head();

    const char *inPath = (env)->GetStringUTFChars(log_path, NULL);
    if (NULL == inPath) return 1;
    (env)->ReleaseStringUTFChars(log_path, inPath);
    std::string strPath(inPath);
    strPath += "/alog.txt";
    OpenFile(strPath.c_str());
    SetLogLevel(0);
    return 0;
}
/*
extern "C" JNIEXPORT jint JNICALL
Java_com_tinyvpn_MyVPNService_pushHttpHeaderFromJni(
        JNIEnv *env, jobject , jobject buf, jint body_length, jint obfu) {
    jbyte *buff = (jbyte *) env->GetDirectBufferAddress(buf);
    VpnPacket packet((char*)buff, 1020, 4096);
    packet.set_back_offset(1020 + body_length);
    if (obfu == 1)
        obfuscation_utl::encode((unsigned char*)packet.data(), 4, g_iv);
    if (obfu == 1)
        obfuscation_utl::encode((unsigned char*)packet.data()+4, body_length-4, g_iv);

    sock_http::push_front_xdpi_head_1(packet);

    return 1020-packet.front_offset();
}
extern "C" JNIEXPORT jint JNICALL
Java_com_tinyvpn_MyVPNService_popFrontXdpiHeadFromJni(
        JNIEnv *env, jobject , jobject buf, jint position, jint length, jint obfu) {
    std::string http_header;
    jbyte *buff = (jbyte *) env->GetDirectBufferAddress(buf);
    http_header.assign((char*)buff + position, length);
    int  http_head_length=0;
    int http_body_length=0;
    if (sock_http::pop_front_xdpi_head(http_header, http_head_length,http_body_length) != 0){  // parse error
        return 0;
    }
    if (http_body_length==0||http_head_length==0) {
        __android_log_write(ANDROID_LOG_DEBUG, "JNI", "parse http header error.");
        return 0;
    }

    if (obfu == 1)
        obfuscation_utl::decode((unsigned char*)buff + position + http_head_length, 4, g_iv);
    if (obfu == 1) {
        obfuscation_utl::decode((unsigned char*)buff+position+http_head_length+4, http_body_length-4, g_iv);
    }
    return http_body_length<<16 | http_head_length;
}*/
extern "C" JNIEXPORT jint JNICALL
Java_com_tinyvpn_MyVPNService_connectServerFromJni(
                JNIEnv *env, jobject thisObj /* this */, jint protocol, jstring ip, jint port, jint premium, jstring androidId, jstring userName, jstring userPassword) {
    INFO("start connect server");
    g_protocol = protocol;
    std::string global_private_ip;
    char* ip_packet_data;

    const char *inIp = (env)->GetStringUTFChars(ip, NULL);
    if (NULL == inIp) return 1;
    std::string strIp(inIp);
    (env)->ReleaseStringUTFChars(ip, inIp);
    const char *inId = (env)->GetStringUTFChars(androidId, NULL);
    if (NULL == inId) return 1;
    std::string strId(inId);
    (env)->ReleaseStringUTFChars(androidId, inId);

    std::string strName;
    if(premium >= 2) {
        const char *inName = (env)->GetStringUTFChars(userName, NULL);
        if (NULL == inName) return 1;
        strName = inName;
        (env)->ReleaseStringUTFChars(userName, inName);
    }
    std::string strPassword;
    if(premium>=2) {
        const char *inPassword = (env)->GetStringUTFChars(userPassword, NULL);
        if (NULL == inPassword) return 1;
        strPassword = inPassword;
        (env)->ReleaseStringUTFChars(userPassword, inPassword);
    }
    __android_log_print(ANDROID_LOG_DEBUG, "JNI", "ip:%s,id:%s,name:%s,password:%s", strIp.c_str(), strId.c_str(), strName.c_str(), strPassword.c_str());

    int sock = 0;
    if (g_protocol == kSslType) {
        if (init_ssl_client() != 0) {
            __android_log_write(ANDROID_LOG_ERROR, "JNI", "init ssl fail.");
            return 1;
        }
        INFO("connect ssl");
        connect_ssl(strIp, port, sock);
        if (sock == 0) {
            __android_log_write(ANDROID_LOG_ERROR, "JNI", "sock is zero.");
            return 1;
        }
    } else if (g_protocol == kHttpType) {
        if (connect_tcp(strIp, port, sock) != 0)
            return 1;
    } else {
        __android_log_write(ANDROID_LOG_ERROR, "JNI", "protocol errror.");
        return 1;
    }


    std::string strPrivateIp;
    INFO("get private_ip");
    if (g_protocol == kSslType)
        get_private_ip(premium,strId,strName,strPassword, strPrivateIp);
    else if (g_protocol == kHttpType) {
        if (get_private_ip_http(premium, strId, strName, strPassword, strPrivateIp) != 0) {
            __android_log_write(ANDROID_LOG_ERROR, "JNI", "get private_ip error.");
            return 1;
        }
    }
    ip_packet_data = (char*)strPrivateIp.c_str();

    g_private_ip = *(uint32_t*)ip_packet_data;
    global_private_ip = socket_utl::socketaddr_to_string(g_private_ip);
    __android_log_print(ANDROID_LOG_INFO, "JNI", "global_private_ip:%s,%s", string_utl::HexEncode(strPrivateIp).c_str(), global_private_ip.c_str());
    //INFO("global_private_ip:%s,%s", string_utl::HexEncode(strPrivateIp).c_str(), global_private_ip.c_str());
    // set client_fd
    jclass thisClass = (env)->GetObjectClass( thisObj);
    //INFO("get class ok.");
    jfieldID fidNumber = (env)->GetFieldID( thisClass, "client_fd", "I");
    //INFO("get field ok.");
    if (NULL == fidNumber)
        return 1;
    //INFO("field not null");
    // Change the variable
    (env)->SetIntField(thisObj, fidNumber, (jint)sock);
    __android_log_print(ANDROID_LOG_INFO, "JNI", "set client_fd:%d", sock);
    //INFO("set client_fd:%d", sock);
    client_sock = sock;

    fidNumber = (env)->GetFieldID( thisClass, "privateIp", "I");
    if (NULL == fidNumber)
        return 1;
    // Change the variable
    (env)->SetIntField(thisObj, fidNumber, ntohl(g_private_ip));
    __android_log_print(ANDROID_LOG_INFO, "JNI", "set private_ip:%x", ntohl(g_private_ip));
    //INFO("get private ip ok.");
    return 0;
}

const int BUF_SIZE = 4096*4;
static char g_tcp_buf[BUF_SIZE*2];
static int g_tcp_len;
int write_tun(char* ip_packet_data, int ip_packet_len){
    int len;
    if (g_tcp_len != 0) {
        if (ip_packet_len + g_tcp_len > sizeof(g_tcp_buf)) {
            __android_log_print(ANDROID_LOG_ERROR, "JNI", "relay size over %lu", sizeof(g_tcp_buf));
            g_tcp_len = 0;
            return 1;
        }
        memcpy(g_tcp_buf + g_tcp_len, ip_packet_data, ip_packet_len);
        ip_packet_data = g_tcp_buf;
        ip_packet_len += g_tcp_len;
        g_tcp_len = 0;
        __android_log_print(ANDROID_LOG_DEBUG, "JNI", "relayed packet:%d", ip_packet_len);
    }

    while(1) {
        if (ip_packet_len == 0)
            break;
        // todo: recv from socket, send to utun1
        if (ip_packet_len < sizeof(struct ip) ) {
            __android_log_print(ANDROID_LOG_ERROR, "JNI","less than ip header:%d.", ip_packet_len);
            memcpy(g_tcp_buf, ip_packet_data, ip_packet_len);
            g_tcp_len = ip_packet_len;
            break;
        }
        struct ip *iph = (struct ip *)ip_packet_data;
        len = ntohs(iph->ip_len);

        if (ip_packet_len < len) {
            if (len > BUF_SIZE) {
                __android_log_print(ANDROID_LOG_ERROR, "JNI","something error1.%x,%x,data:%s",len, ip_packet_len, string_utl::HexEncode(std::string(ip_packet_data,ip_packet_len)).c_str());
                g_tcp_len = 0;
            } else {
                __android_log_print(ANDROID_LOG_DEBUG, "JNI","relay to next packet:%d,current buff len:%d", ip_packet_len, g_tcp_len);
                if (g_tcp_len == 0) {
                    memcpy(g_tcp_buf +g_tcp_len, ip_packet_data, ip_packet_len);
                    g_tcp_len += ip_packet_len;
                }
            }
            break;
        }

        if (len > BUF_SIZE) {
            __android_log_print(ANDROID_LOG_ERROR, "JNI","something error.%x,%x",len, ip_packet_len);
            g_tcp_len = 0;
            break;
        } else if (len == 0) {
            __android_log_print(ANDROID_LOG_ERROR, "JNI","len is zero.%x,%x",len, ip_packet_len); //string_utl::HexEncode(std::string(ip_packet_data,ip_packet_len)).c_str());
            g_tcp_len = 0;
            break;
        }

        char ip_src[INET_ADDRSTRLEN + 1];
        char ip_dst[INET_ADDRSTRLEN + 1];
        inet_ntop(AF_INET,&iph->ip_src.s_addr,ip_src, INET_ADDRSTRLEN);
        inet_ntop(AF_INET,&iph->ip_dst.s_addr,ip_dst, INET_ADDRSTRLEN);

        __android_log_print(ANDROID_LOG_DEBUG, "JNI","send to utun, from(%s) to (%s) with size:%d",ip_src,ip_dst,len);
//        if (sys_utl::tun_dev_write(g_fd_tun_dev, (void*)ip_packet_data, len) <= 0) {
        if (::write(g_fd_tun_dev, (void*)ip_packet_data, len) <= 0) {
            __android_log_print(ANDROID_LOG_ERROR, "JNI","write tun error:%d", g_fd_tun_dev);
            return 1;
        }
        ip_packet_len -= len;
        ip_packet_data += len;
    }
    return 0;
}
int write_tun_http(char* ip_packet_data, int ip_packet_len) {
    static uint32_t g_iv = 0x87654321;
    int len;
    if (g_tcp_len != 0) {
        if (ip_packet_len + g_tcp_len > sizeof(g_tcp_buf)) {
            INFO("relay size over %d", sizeof(g_tcp_buf));
            g_tcp_len = 0;
            return 1;
        }
        memcpy(g_tcp_buf + g_tcp_len, ip_packet_data, ip_packet_len);
        ip_packet_data = g_tcp_buf;
        ip_packet_len += g_tcp_len;
        g_tcp_len = 0;
        INFO("relayed packet:%d", ip_packet_len);
    }
    std::string http_packet;
    int http_head_length, http_body_length;
    while (1) {
        if (ip_packet_len == 0)
            break;
        http_packet.assign(ip_packet_data, ip_packet_len);
        if (sock_http::pop_front_xdpi_head(http_packet, http_head_length, http_body_length) != 0) {  // decode http header fail
            __android_log_print(ANDROID_LOG_DEBUG, "JNI","relay to next packet:%d,current buff len:%d", ip_packet_len, g_tcp_len);
            if (g_tcp_len == 0) {
                memcpy(g_tcp_buf + g_tcp_len, ip_packet_data, ip_packet_len);
                g_tcp_len += ip_packet_len;
            }
            break;
        }
        ip_packet_len -= http_head_length;
        ip_packet_data += http_head_length;
        obfuscation_utl::decode((unsigned char *) ip_packet_data, 4, g_iv);
        obfuscation_utl::decode((unsigned char *) ip_packet_data + 4, http_body_length - 4, g_iv);

        struct ip *iph = (struct ip *) ip_packet_data;
        len = ntohs(iph->ip_len);
        char ip_src[INET_ADDRSTRLEN + 1];
        char ip_dst[INET_ADDRSTRLEN + 1];
        inet_ntop(AF_INET, &iph->ip_src.s_addr, ip_src, INET_ADDRSTRLEN);
        inet_ntop(AF_INET, &iph->ip_dst.s_addr, ip_dst, INET_ADDRSTRLEN);

        __android_log_print(ANDROID_LOG_DEBUG, "JNI","send to tun,http, from(%s) to (%s) with size:%d, header:%d,body:%d", ip_src,
              ip_dst, len, http_head_length, http_body_length);
        sys_utl::tun_dev_write(g_fd_tun_dev, (void *) ip_packet_data, len);

        ip_packet_len -= http_body_length;
        ip_packet_data += http_body_length;
    }
    return 0;
}
extern "C" JNIEXPORT jint JNICALL
Java_com_tinyvpn_MyVPNService_startSslThreadFromJni(
        JNIEnv *env, jobject thisObj /* this */, jint tun_fd) {
    g_fd_tun_dev = tun_fd;
    INFO("start ssl thread");
    __android_log_print(ANDROID_LOG_DEBUG, "JNI", "start ssl thread:%d,g_fd_tun_dev:%d,client_sock:%d", tun_fd, g_fd_tun_dev, client_sock);
    g_isRun= 1;
    g_in_recv_tun = 1;
    g_tcp_len = 0;
    in_traffic = 0;
    out_traffic = 0;
    //std::thread tun_thread(client_recv_tun, client_sock);

    g_in_recv_socket = 1;
    int ip_packet_len;
    char ip_packet_data[BUF_SIZE];
    int ret;
    time_t lastTime = time_utl::localtime();
    time_t currentTime;
    time_t recvTime = time_utl::localtime();
    time_t sendTime = time_utl::localtime();

    fd_set fdsr;
    int maxfd;
    while(g_isRun == 1){
        FD_ZERO(&fdsr);
        FD_SET(client_sock, &fdsr);
        FD_SET(g_fd_tun_dev, &fdsr);
        maxfd = std::max(client_sock, g_fd_tun_dev);
        struct timeval tv_select;
        tv_select.tv_sec = 2;
        tv_select.tv_usec = 0;
        int nReady = select(maxfd + 1, &fdsr, NULL, NULL, &tv_select);
        if (nReady < 0) {
            ERROR("select error:%d", nReady);
            __android_log_print(ANDROID_LOG_ERROR, "JNI", "select error:%d", nReady);
            break;
        } else if (nReady == 0) {
            //ERROR("select timeout");
            __android_log_print(ANDROID_LOG_DEBUG, "JNI", "select timeout");
            continue;
        }

        if (FD_ISSET(g_fd_tun_dev, &fdsr)) {  // recv from tun
            static VpnPacket vpn_packet(4096);
            int readed_from_tun;
            vpn_packet.reset();
            readed_from_tun = sys_utl::tun_dev_read(g_fd_tun_dev, vpn_packet.data(), vpn_packet.remain_size());
            vpn_packet.set_back_offset(vpn_packet.front_offset()+readed_from_tun);
            if(readed_from_tun < sizeof(struct ip)) {
                __android_log_print(ANDROID_LOG_ERROR, "JNI","tun_dev_read error, size:%d", readed_from_tun);
                ERROR("tun_dev_read error");
                break;
            }
            if(readed_from_tun > 0)
            {
                struct ip *iph = (struct ip *)vpn_packet.data();

                char ip_src[INET_ADDRSTRLEN + 1];
                char ip_dst[INET_ADDRSTRLEN + 1];
                inet_ntop(AF_INET,&iph->ip_src.s_addr,ip_src, INET_ADDRSTRLEN);
                inet_ntop(AF_INET,&iph->ip_dst.s_addr,ip_dst, INET_ADDRSTRLEN);

                if(g_private_ip != iph->ip_src.s_addr) {
                    __android_log_print(ANDROID_LOG_ERROR, "JNI","src_ip mismatch:%x,%x",g_private_ip, iph->ip_src.s_addr);
                    continue;
                }
                __android_log_print(ANDROID_LOG_DEBUG, "JNI","recv from tun, from(%s) to (%s) with size:%d",ip_src,ip_dst,readed_from_tun);
                //file_utl::write(sockid, vpn_packet.data(), readed_from_tun);
                out_traffic += readed_from_tun;
                if (g_protocol == kSslType) {
                    if (ssl_write(vpn_packet.data(), readed_from_tun) != 0) {
                        __android_log_print(ANDROID_LOG_ERROR, "JNI", "ssl_write error!");
                        ERROR("ssl_write error");
                        break;
                    }
                } else if (g_protocol == kHttpType){
                    http_write(vpn_packet);
                }
                sendTime = time_utl::localtime();
            }
//            if (--nReady == 0)  // read over
//                continue;
        }
        if (FD_ISSET(client_sock, &fdsr)) {  // recv from socket
            ip_packet_len = 0;
            if (g_protocol == kSslType) {
                ret = ssl_read(ip_packet_data, ip_packet_len);
                if (ret != 0) {
                    __android_log_print(ANDROID_LOG_ERROR, "JNI", "ssl_read error!");
                    ERROR("ssl_read error");
                    break;
                }
            } else if (g_protocol == kHttpType) {
                ip_packet_len = file_utl::read(client_sock, ip_packet_data, BUF_SIZE);
            } else {
                ERROR("protocol error.");
                break;
            }
            if (ip_packet_len == 0)
                continue;
            in_traffic += ip_packet_len;
            __android_log_print(ANDROID_LOG_DEBUG, "JNI", "recv from socket, size:%d",
                                ip_packet_len);
            if (g_protocol == kSslType) {
                if (write_tun((char *) ip_packet_data, ip_packet_len) != 0) {
                    __android_log_print(ANDROID_LOG_ERROR, "JNI", "write_tun error");
                    ERROR("write_tun error");
                    break;
                }
            } else if (g_protocol == kHttpType){
                if (write_tun_http((char *) ip_packet_data, ip_packet_len) != 0) {
                    __android_log_print(ANDROID_LOG_ERROR, "JNI", "write_tun error");
                    ERROR("write_tun error");
                    break;
                }
            }
            recvTime = time_utl::localtime();
        }
        currentTime = time_utl::localtime();
        if (currentTime - recvTime > 60 || currentTime - sendTime > 60) {
            __android_log_print(ANDROID_LOG_ERROR, "JNI","send or recv timeout");
            break;
        }
        if (currentTime - lastTime >= 1) {
            jclass thisClass = (env)->GetObjectClass(thisObj);
            jfieldID fidNumber = (env)->GetFieldID(thisClass, "current_traffic", "I");
            if (NULL == fidNumber) {
                ERROR("current_traffic error");
                return 1;
            }
            // Change the variable
            (env)->SetIntField(thisObj, fidNumber, in_traffic + out_traffic);
            __android_log_print(ANDROID_LOG_INFO, "JNI", "current traffic:%d", in_traffic+out_traffic);
            jmethodID mtdCallBack = (env)->GetMethodID(thisClass, "trafficCallback", "()I");
            if (NULL == mtdCallBack) {
                ERROR("trafficCallback error");
                return 1;
            }
            ret = (env)->CallIntMethod(thisObj, mtdCallBack);
            lastTime = time_utl::localtime();
        }
    }

    __android_log_print(ANDROID_LOG_INFO, "JNI","main thread stop");
    if(g_protocol == kSslType)
        ssl_close();
    else if (g_protocol == kHttpType)
        close(client_sock);
    g_isRun = 0;

    // callback to app
    jclass thisClass = (env)->GetObjectClass( thisObj);
    jmethodID midCallBackAverage = (env)->GetMethodID(thisClass,   "stopCallback", "()I");
    if (NULL == midCallBackAverage)
        return 1;
    ret = (env)->CallIntMethod(thisObj, midCallBackAverage);
    __android_log_print(ANDROID_LOG_DEBUG, "JNI", "stop callback:%d", ret);
    return 0;
}
extern "C" JNIEXPORT jint JNICALL
Java_com_tinyvpn_MyVPNService_stopRunFromJni(
        JNIEnv *env, jobject /* this */)
{
    g_isRun = 0;
    __android_log_print(ANDROID_LOG_INFO, "JNI", "set stop ok");
    return 0;
}

