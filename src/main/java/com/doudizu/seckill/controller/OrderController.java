package com.doudizu.seckill.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.doudizu.seckill.conf.PropertiesConf;
import com.doudizu.seckill.domain.Order;
import com.doudizu.seckill.domain.Product;
import com.doudizu.seckill.redis.OrderKey;
import com.doudizu.seckill.redis.RedisClusterService;
import com.doudizu.seckill.redis.RedisService;
import com.doudizu.seckill.service.OrderService;
import com.doudizu.seckill.service.ProductService;
import com.doudizu.seckill.util.HttpClient;
import com.doudizu.seckill.util.JsonGenerate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
public class OrderController {
    @Autowired
    PropertiesConf propertiesConf;

    @Autowired
    ProductService productService;

    @Autowired
    OrderService orderService;

    @Autowired
    RedisService redisService;

    @Autowired
    RedisClusterService redisClusterService;

    //全部订单接口
    @RequestMapping("/result")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getOrdersByUid(@RequestParam("uid") int uid) {
        Map<String, Object> returnMap = new HashMap<>();
        returnMap.put("data", orderService.getOrdersByUid(uid));
        return new ResponseEntity<>(returnMap, HttpStatus.OK);
    }

    //订单接口
    @RequestMapping(value = "/order", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Map> createOrder(HttpServletRequest request, @RequestBody Map<String, String> map) {
        Map<String, Object> returnMap = new HashMap<>();
        log.info(request.getQueryString());
        String uid = map.get("uid");
        String pidStr = map.get("pid");
        String sessionid = request.getHeader("sessionid");
        log.info("sessionid:" + sessionid);
        //拿到redis上sessionid对应的uid
        long requestUid = Long.valueOf(redisService.getKey(OrderKey.getByOrderId, sessionid));
        String ip = request.getHeader("X-Forwarded-For");
        log.info("requestUid:" + requestUid + " ip:" + ip);

        String order_id = uid + "~" + pidStr + "~" + System.currentTimeMillis() / 1000;

        //判断请求的uid和参数中uid是否一致,ip黑名单
        //请求的uid和参数中uid不一致,ip黑名单

        if (!redisService.verifyall(uid, sessionid, ip) || !redisClusterService.verify(uid)) {
            log.info("作弊用户" + "uid:" + uid + " ip:" + ip + " sessionid:" + sessionid);
            return new ResponseEntity<>(returnMap, HttpStatus.FORBIDDEN);
        }
        if (redisClusterService.createorder(uid, pidStr, order_id)) {
            returnMap.put("code", 0);
            returnMap.put("order_id", order_id);
            log.info("下单成功" + order_id);
        } else {
            returnMap.put("code", 1);
            log.info("下单失败" + order_id);
        }

        //请求的uid和参数中uid一致
        //判断库存
        /*Product product = productService.getProductByPid(pid);
        int stock = product.getCount();
        if (stock <= 0) {
            log.info("商品" + pid + "库存不够，不可下单");
            returnMap.put("code", 1);
            return new ResponseEntity<>(returnMap, HttpStatus.OK);
        }*/

        /*List<Order> orders = orderService.getOrderByUidAndPid(uid, pid);
        if (orders.size() >= 1) {
            log.info("商品" + pid + "用户" + uid + "已经购买过");
            returnMap.put("code", 1);
            log.info(JSONObject.toJSON(returnMap).toString());
            return new ResponseEntity<>(returnMap, HttpStatus.OK);
        }
        //减库存 下订单 写入秒杀订单
        log.info("商品" + pid + "用户" + uid + "可下单");
        String orderId = orderService.createOrder(uid, pid);
        log.info("orderId:" + orderId);
        returnMap.put("code", 0);
        returnMap.put("order_id", orderId);*/
        return new ResponseEntity<>(returnMap, HttpStatus.OK);
    }

    //支付接口
    @RequestMapping(value = "/pay", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Map> payOrder(HttpServletRequest request, @RequestBody Map<String, String> map) {
        log.info(map.toString());
        Map<String, Object> returnMap = new HashMap<>();
        int uid = Integer.valueOf(map.get("uid"));
        int price = Integer.valueOf(map.get("price"));
        String orderId = map.get("order_id");
        String sessionid = request.getHeader("sessionid");
        log.info("sessionid为" + sessionid);
        //拿到redis上sessionid对应的uid，即requestUid
        int requestUid = Integer.valueOf(redisService.getKey(OrderKey.getByOrderId, sessionid));
        log.info("requestUid:" + requestUid);
        //判断请求的uid和参数中uid是否一致
        //请求的uid和参数中uid不一致，返回403
        if (requestUid != uid) {
            return new ResponseEntity<>(returnMap, HttpStatus.FORBIDDEN);
        }
        //请求的uid和参数中uid一致
        //通过下面url获取token
        String url = propertiesConf.getPayUrl() + ":" + propertiesConf.getPayPort() + propertiesConf.getPayPath();
        JSONObject json = JsonGenerate.generatePayJsonString(uid, price, orderId);
        log.info(url);
        log.info(json.toString());
        String tokenJsonStr = HttpClient.httpPostWithJSON(url, json.toString());
        log.info(tokenJsonStr);
        JSONObject tokenJson = JSON.parseObject(tokenJsonStr);
        String token = (String) tokenJson.get("token");
        int code = orderService.payOrder(token, uid, price, orderId);
        log.info("code:" + code);
        log.info("token:" + token);
        if (code == 0) {
            returnMap.put("code", code);
            returnMap.put("token", token);
        } else {
            returnMap.put("code", code);
        }
        log.info(JSON.toJSONString(returnMap));
        return new ResponseEntity<>(returnMap, HttpStatus.OK);
    }
}
