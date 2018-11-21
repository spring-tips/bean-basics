package com.example.demo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.PropertiesBeanDefinitionReader;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Map;

import static com.example.demo.SpringUtils.isActive;

@EnableAutoConfiguration
@Configuration
public class DemoApplication {

	static final String FN = "fn";
	static final String XML = "xml";
	static final String JC = "jc";
	static final String CS = "cs";
	static final String PF = "pf";

	public static void main(String[] args) {

		ConfigurableApplicationContext applicationContext = new SpringApplicationBuilder()
			.initializers(new FunctionalDemo(), new ProfileAwareInitializer())
			.sources(DemoApplication.class)
			.run(args);
		confirm(applicationContext, DataSource.class, 1);
		confirm(applicationContext, CustomerService.class, 1);
	}

	private static void confirm(ApplicationContext ac, Class<?> clzz, int count) {
		Map<String, ?> beansOfType = ac.getBeansOfType(clzz);
		Assert.isTrue(beansOfType.size() == count,
			"there should be " + count + " instances of " + clzz.getName());
	}

	private static class ProfileAwareInitializer
		implements ApplicationContextInitializer<GenericApplicationContext> {

		@Override
		public void initialize(GenericApplicationContext ac) {
			if (isActive(ac, PF)) {
				PropertiesBeanDefinitionReader pdr = new PropertiesBeanDefinitionReader(ac);
				pdr.loadBeanDefinitions("/properties-demo.properties");
			}
			if (isActive(ac, JC)) {
				ac.registerBean(JavaConfigDemo.class);
			}
			if (isActive(ac, XML)) {
				ac.registerBean(XmlDemo.class);
			}
			if (isActive(ac, CS)) {
				ac.registerBean(ComponentScanDemo.class);
			}
		}
	}
}

class SpringUtils {

	static boolean isActive(GenericApplicationContext ac, String p) {
		return Arrays.asList(ac.getEnvironment().getActiveProfiles()).contains(p);
	}
}

@Profile(DemoApplication.JC)
@Configuration
class JavaConfigDemo {

	@Bean
	DataSource dataSource() {
		return new EmbeddedDatabaseBuilder()
			.setType(EmbeddedDatabaseType.H2)
			.build();
	}

	@Bean
	CustomerService customerService(DataSource dataSource) {
		return new CustomerService(dataSource, DemoApplication.JC);
	}

}

@ComponentScan
@Configuration
@Profile(DemoApplication.CS)
class ComponentScanDemo {

	@Bean
	DataSource dataSource() {
		return new EmbeddedDatabaseBuilder()
			.setType(EmbeddedDatabaseType.H2)
			.build();
	}
}

class FunctionalDemo
	implements ApplicationContextInitializer<GenericApplicationContext> {

	@Override
	public void initialize(GenericApplicationContext context) {
		if (isActive(context, DemoApplication.FN)) {
			context.registerBean(DataSource.class, () ->
				new EmbeddedDatabaseBuilder()
					.setType(EmbeddedDatabaseType.H2)
					.build());

			context.registerBean(CustomerService.class, () -> {
				DataSource dataSource = context.getBean(DataSource.class);
				return new CustomerService(dataSource, DemoApplication.FN);
			});
		}
	}
}

@Configuration
@Profile(DemoApplication.XML)
@ImportResource("/xml-demo.xml")
class XmlDemo {
}

@Service
class CustomerService {

	private final Log log = LogFactory.getLog(getClass());
	private final DataSource dataSource;

	CustomerService(DataSource dataSource,
																	@Value("cs") String m) {
		this.dataSource = dataSource;
		log.info("injected the datasource " + this.dataSource.toString());
		log.info("config style is '" + m + "'");
	}
}
