package com.example.jafu;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.r2dbc.function.DatabaseClient;
import org.springframework.fu.jafu.ApplicationDsl;
import org.springframework.fu.jafu.ConfigurationDsl;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

import static org.springframework.fu.jafu.ApplicationDsl.application;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

public class JafuApplication {

	private static ApplicationDsl app = application(app ->
		app.importConfiguration(Config.dataConfig)
			.importConfiguration(Config.webConfig)
			.listener(ApplicationReadyEvent.class, e -> app.ref(UserRepository.class).init())
	);

	public static void main(String[] args) {
		System.setProperty("org.springframework.boot.logging.LoggingSystem", "org.springframework.boot.logging.java.JavaLoggingSystem");
		app.run(args);
	}
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class User {
	private String login, firstname, lastname;
}

class UserHandler {

	private final UserRepository repository;

	public UserHandler(UserRepository repository) {
		this.repository = repository;
	}

	public Mono<ServerResponse> listApi(ServerRequest request) {
		return ok()
			.contentType(MediaType.APPLICATION_JSON_UTF8)
			.body(repository.findAll(), User.class);
	}

}


class Config {

	static Consumer<ConfigurationDsl> dataConfig = conf ->
		conf
			.beans(beans -> beans.bean(UserRepository.class))
			.r2dbc(db -> db
				.database("orders")
				.username("orders")
			);

	static Consumer<ConfigurationDsl> webConfig =
		conf ->
			conf
				.beans(beans -> beans.bean(UserHandler.class))
				.server(server ->
					server
						.port(8080)
						.router(router -> {
							UserHandler userHandler = conf.ref(UserHandler.class);
							router.GET("/", userHandler::listApi);
						})
						.codecs(codecs -> codecs.string().jackson())
				);
}

@Log4j2
class UserRepository {

	private final DatabaseClient client;

	public UserRepository(DatabaseClient client) {
		this.client = client;
	}

	public Mono<Integer> count() {
		return client.execute().sql("SELECT COUNT(*) FROM users").as(Integer.class).fetch().one();
	}

	public Flux<User> findAll() {
		return client.select().from("users").as(User.class).fetch().all();
	}

	public Mono<User> findOne(String id) {
		return client.execute().sql("SELECT * FROM users WHERE login = $1").bind(1, id).as(User.class).fetch().one();
	}

	public Mono<Void> deleteAll() {
		return client.execute().sql("DELETE FROM users").fetch().one().then();
	}

	public Mono<String> save(User user) {
		return client
			.insert()
			.into(User.class)
			.table("users")
			.using(user)
			.exchange()
			.flatMap(it -> it.extract((r, m) -> r.get("login", String.class)).one());
	}

	public void init() {
		client
			.execute()
			.sql("CREATE TABLE IF NOT EXISTS users (login varchar PRIMARY KEY, firstname varchar, lastname varchar);")
			.fetch()
			.one()
			.thenMany(deleteAll())
			.thenMany(save(new User("smaldini", "Stéphane", "Maldini")))
			.thenMany(save(new User("jlong", "Josh", "Long")))
			.thenMany(save(new User("sdeleuze", "Sébastien", "Deleuze")))
			.thenMany(save(new User("bclozel", "Brian", "Clozel")))
			.subscribe(log::info);


	}
}