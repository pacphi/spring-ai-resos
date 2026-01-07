package me.pacphi.ai.resos;

import me.pacphi.ai.resos.mcp.ResOsService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class SpringAiResOsMcpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringAiResOsMcpServerApplication.class, args);
	}

	@Bean
	public List<ToolCallback> resOsTools(ResOsService resOsService) {
		return List.of(ToolCallbacks.from(resOsService));
	}

}
