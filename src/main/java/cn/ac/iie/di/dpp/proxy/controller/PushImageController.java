package cn.ac.iie.di.dpp.proxy.controller;

import cn.ac.iie.di.commons.httpserver.framework.handler.HandlerI;
import cn.ac.iie.di.dpp.common.Constants;
import cn.ac.iie.di.dpp.common.DockerConfig;
import cn.ac.iie.di.dpp.handler.DockerImageHandler;
import cn.ac.iie.di.dpp.handler.Impl.DockerImageHandlerImpl;
import cn.ac.iie.di.dpp.main.ProxyMain;
import cn.ac.iie.di.dpp.proxy.RegistryProxyServer;
import cn.ac.iie.di.dpp.util.UnCompressUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Fighter Created on 2018/10/8.
 */
public class PushImageController implements HandlerI {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushImageController.class);
    private DockerImageHandler dockerImageHandler = new DockerImageHandlerImpl(DockerConfig.getDockerClient());

    private static final String Dockerfilepath;
    private static final String bootScriptPath;

    static {
        Dockerfilepath = ClassLoader.getSystemClassLoader().getResource("Dockerfile.properties").getFile();
        bootScriptPath = ClassLoader.getSystemClassLoader().getResource("myStart.sh").getFile();
    }

    //imagePath:url_test
    @Override
    public void execute(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws Exception {
        try {
            //每收到一个请求 计数器 +1
            RegistryProxyServer.count.incrementAndGet();
            LOGGER.info("counter: {}", RegistryProxyServer.count.get());

            Map<String, String[]> paramMap = request.getParameterMap();
            String imagePath = paramMap.get("imagePath")[0];
            LOGGER.info("receive request: imagePath {}", imagePath);

            if (!Files.exists(Paths.get(imagePath))) {
                LOGGER.info("image tar: {} not found!", imagePath);
                //请求返回时 计数器 -1
                RegistryProxyServer.count.decrementAndGet();
                LOGGER.info("counter: {}", RegistryProxyServer.count.get());
                Map errMsg = new HashMap();
                errMsg.put("code", 404);
                errMsg.put("msg", "upload image error, image not found!");
                response.setStatus(HttpServletResponse.SC_OK, "image not found!");
                response.getWriter().print(JSON.toJSONString(errMsg));
                return;
            }
            Map<String, Object> map = new HashMap<>();
            //解压，创建解压文件夹
            unCompressImageTar(imagePath, map);
            String desDir = (String) map.get("desDir");
            //先load
            dockerImageHandler.load(imagePath);
            //build：修改dockerfile模板后build
            build(map);
            //push
            dockerImageHandler.push((String) map.get("newImageAndTag"));

            //请求返回时 计数器 -1
            RegistryProxyServer.count.decrementAndGet();
            LOGGER.info("counter: {}", RegistryProxyServer.count.get());
            //return
            map.put("code", 200);
            response.getWriter().print(JSON.toJSONString(map));
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().flush();
        } catch (Exception e) {
            LOGGER.error("push image error! {}", ExceptionUtils.getFullStackTrace(e));
            //请求返回时 计数器 -1
            RegistryProxyServer.count.decrementAndGet();
            LOGGER.info("counter: {}", RegistryProxyServer.count.get());

            Map errMsg = new HashMap();
            errMsg.put("code", 400);
            errMsg.put("msg", e.getMessage());
            response.setStatus(HttpServletResponse.SC_OK, "upload image error.");
            response.getWriter().print(JSON.toJSONString(errMsg));
        }
    }

    //build 成新 image
    private void build(Map<String, Object> map) throws Exception {
        //新tag名为：oldImageName_:oldTag
        String oldImageNameTag = (String) map.get("RepoTags");
        String[] repoTags = oldImageNameTag.split(":");
        String imageName = repoTags[0];
        String tag = repoTags[1];
//            String buildImageAndTag = imageName + "_iie:" + tag;
        String newImageName = new StringBuffer(ProxyMain.conf.getString(Constants.REGISTRY_REPO_NAME))
                .append("/")
                .append(ProxyMain.conf.getString(Constants.REGISTRY_PROJECT_NAME))
                .append("/")
                .append(imageName).toString();
        dockerImageHandler.tag(oldImageNameTag, newImageName, tag);

        String newImageAndTag = new StringBuffer(newImageName).append(":").append(tag).toString();

        //修改Dockerfile模板，复制一份到解压文件中
        String desDir = (String) map.get("desDir");
        String buildDockerfilePath = desDir + File.separator + "Dockerfile";
        int copyDockerfileRet = IOUtils.copy(new FileInputStream(Dockerfilepath), new FileOutputStream(buildDockerfilePath));
        if (copyDockerfileRet < 0) {
            throw new Exception("copy Dockerfile.properties template error!");
        }

        String bootScriptCopyPath = desDir + File.separator + "myStart.sh";
        int copyShellRet = IOUtils.copy(new FileInputStream(bootScriptPath), new FileOutputStream(bootScriptCopyPath));
        if (copyShellRet < 0) {
            throw new Exception("copy boot shell script error!");
        }

        List<String> lines = Files.readAllLines(Paths.get(buildDockerfilePath));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("FROM")) {
                lines.set(i, line.replaceFirst("(?<=FROM )(.*)", oldImageNameTag));
                break;
            }
        }
        //修改模板副本文件
        IOUtils.writeLines(lines, IOUtils.LINE_SEPARATOR, new FileOutputStream(buildDockerfilePath));
        String imageID = dockerImageHandler.build(buildDockerfilePath, newImageAndTag);
        map.put("imageID", imageID);
        map.put("newImageAndTag", newImageAndTag);
    }

    //返回压缩文件目录、镜像名:标签
    private void unCompressImageTar(String imagePath, Map<String, Object> map) throws Exception {
        LOGGER.info("uncompressing image tar ....");
        try {
            String desDir = imagePath.substring(0, imagePath.lastIndexOf("."));
            UnCompressUtils.unTar(new File(imagePath), desDir);
            map.put("desDir", desDir);
            getRepoTags(desDir, map);
        } catch (Exception e) {
            LOGGER.error("uncompress error! {}", ExceptionUtils.getFullStackTrace(e));
            throw new Exception(e);
        }
    }

    //    @Test
    private void getRepoTags(String desDir, Map<String, Object> map) throws IOException {
        try {
            //读取压缩文件目录内的 manifest.json
            InputStream inputStream = new FileInputStream(desDir + File.separator + "manifest.json");
            String jsonStr = IOUtils.toString(inputStream, "utf8");
            JSONObject jsonObject = JSONObject.parseArray(jsonStr).getJSONObject(0);
            String repoTags = jsonObject.getJSONArray("RepoTags").getString(0);
            String imageID = jsonObject.getString("Config");
            map.put("RepoTags", repoTags);
            map.put("imageID", imageID);
        } catch (FileNotFoundException e) {
            LOGGER.error("manifest.json file not found!");
            throw e;
        } catch (NullPointerException e) {
            LOGGER.error("'RepoTags' value in manifest.json not exist!");
            throw e;
        }
    }
}
