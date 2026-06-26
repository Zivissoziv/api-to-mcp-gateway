package com.example.mcpgateway.gateway.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mcpgateway.gateway.infrastructure.persistence.row.GatewayCallRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GatewayCallMapper extends BaseMapper<GatewayCallRow> {

    @Select("SELECT COUNT(*) AS totalCalls, "
            + "COUNT(DISTINCT server_code) AS uniqueServers, "
            + "COUNT(DISTINCT tool_name) AS uniqueTools, "
            + "COUNT(DISTINCT client_ip) AS uniqueIps "
            + "FROM gateway_calls")
    Map<String, Object> selectSummary();

    @Select("SELECT server_code AS serverCode, "
            + "COUNT(*) AS callCount, "
            + "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount, "
            + "COUNT(DISTINCT client_ip) AS uniqueIps, "
            + "AVG(duration_ms) AS avgDurationMs, "
            + "MAX(created_at) AS lastCallAt "
            + "FROM gateway_calls "
            + "GROUP BY server_code "
            + "ORDER BY callCount DESC")
    List<Map<String, Object>> selectStatsByServer();

    @Select("SELECT server_code AS serverCode, "
            + "tool_name AS toolName, "
            + "COUNT(*) AS callCount, "
            + "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount, "
            + "COUNT(DISTINCT client_ip) AS uniqueIps, "
            + "AVG(duration_ms) AS avgDurationMs, "
            + "MAX(created_at) AS lastCallAt "
            + "FROM gateway_calls "
            + "GROUP BY server_code, tool_name "
            + "ORDER BY callCount DESC")
    List<Map<String, Object>> selectStatsByTool();

    @Select("SELECT client_ip AS clientIp, "
            + "COUNT(*) AS callCount, "
            + "MAX(created_at) AS lastCallAt "
            + "FROM gateway_calls "
            + "GROUP BY client_ip "
            + "ORDER BY callCount DESC")
    List<Map<String, Object>> selectStatsByIp();

    @Select("SELECT tool_name AS toolName, "
            + "COUNT(*) AS callCount, "
            + "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount, "
            + "COUNT(DISTINCT client_ip) AS uniqueIps, "
            + "AVG(duration_ms) AS avgDurationMs, "
            + "MAX(created_at) AS lastCallAt "
            + "FROM gateway_calls "
            + "WHERE server_code = #{serverCode} AND tool_name IS NOT NULL "
            + "GROUP BY tool_name "
            + "ORDER BY callCount DESC")
    List<Map<String, Object>> selectStatsByServerCode(String serverCode);

    @Select("SELECT client_ip AS clientIp, "
            + "COUNT(*) AS callCount, "
            + "MAX(created_at) AS lastCallAt "
            + "FROM gateway_calls "
            + "WHERE server_code = #{serverCode} "
            + "GROUP BY client_ip "
            + "ORDER BY callCount DESC")
    List<Map<String, Object>> selectIpStatsByServerCode(String serverCode);
}
