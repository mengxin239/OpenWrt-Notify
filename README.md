# OpenWrt-Notify
将OpenWrt提醒信息推送到小米手环
# 保持OpenWrt Notify挂在手机后台，给他允许自启动啥的打开

几十分钟写出来的东西，主打的就是能用就行，然后稍微写了一下wifi断开和检测网段是否正确 ~~所以说和家里网段一样的wifi连上去都会弹Connect Failed(x)~~

然后可以设置一个systemctl守护一下python进程

# 如何部署

两个端都不能直接Ctrl+CV(但是如果你的OpenWrt也是192.168.0.0/24的话可以直接用我的apk，我会放到release)

## 服务端

```
opkg update
opkg install git python3 python3-pip
pip install aiohttp
git clone https://github.com/mengxin239/OpenWrt-Notify
cd OpenWrt-Notify
rm -rf client
cp server/* ./
rm -rf server
```

然后打开server.py,修改里面的Token和ChatID（这个和关闭Telegram提醒二选一）

### 关闭Telegram提醒 （建议开启）
将
```
self.enable_telegram_notify = True
```
改为
```
self.enable_telegram_notify = False
```
即可

至于Token和Chat_ID怎么获取、守护进程怎么设置我就不教了，网上一搜一大把

## 客户端

### 网段为192.168.0.0/24（即第三位为0的），并且你的OpenWrt地址为192.168.0.1，可以直接用我编译出的Apk(在release),其他网段或者OpenWrt地址不是0.1的就继续看

克隆仓库，打开client（直接用AndroidStudio打开路径就行）

修改 "app\src\main\java\com\mengxin239\notifyapp\MainActivity.kt"
```
private val serverAddr = "ws://192.168.0.1:8080/ws"
```
这一行,直接改掉ip地址就行，然后手机插到电脑打开开发者模式直接使用AndroidStudio编译运行（Shift+F10）即可

打开"OpenWrt Notify"的通知权限，后台运行权限，关掉省电，打开自启，然后Mi Fitness打开消息提醒，选择"OpenWrt Notify",至此手机端配置完成

## OpenWrt推送

打开[微信推送](http://192.168.0.1/cgi-bin/luci/admin/services/wechatpush)（这里超链接针对192.168.0.1写的，其他自己改下地址

推送模式：自定义推送

自定义推送内容
```
{	
	"url": "http:///192.168.0.1:8080/notification", 
	"data": "@${tempjsonpath}",
	"content_type": "Content-Type: application/json",
	"str_title_start": "",
	"str_title_end": "",
	"str_linefeed": "\\n",
	"str_splitline": "\\n----\\n",
	"str_space": " ",
	"str_tab": "    ",
	"type":
	  {
		"text":"\"${1}${str_splitline}${nowtime}${2}\""
	  }
}
```
然后把ip地址改一下，保存并应用

# 最后

这个项目耗费时间最多的就是这个README.md,整个项目用了不到一小时写出来的，主要是有ChatGPT加持（x

如果有问题的话发issue（虽然大概率要等一段时间） 主要是为了后面别人配置时候出问题好避坑


Telegram群：[@mengxin239offical](https://t.me/mengxin239offical)
Telegram频道：[@mengxin239OfficialChannel](https://t.me/mengxin239OfficialChannel)
