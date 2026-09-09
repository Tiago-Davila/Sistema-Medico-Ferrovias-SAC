package com.ferrovias.sismedico.comun;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Autenticación contra Active Directory y cierre de toda la superficie de API.
 *
 * <p><b>FR-034</b>: el operario se identifica con las credenciales corporativas.
 * La aplicación no administra usuarios ni contraseñas propias, y no tiene dónde
 * guardarlas.
 *
 * <p><b>FR-036e</b>: toda petición no autenticada se rechaza, incluidas las que
 * no pasan por la pantalla. No hay ruta abierta: ni la documentación de OpenAPI,
 * que describe la forma de los datos clínicos.
 *
 * <p><b>FR-036, FR-036d</b>: hay un único perfil funcional, el de operario, con
 * acceso a las fichas de cualquier legajo. No hay roles, no hay segmentación por
 * sección ni por área, y no se prepara el terreno para tenerlas: segmentar sería
 * funcionalidad nueva, fuera del alcance cerrado de esta feature.
 */
@Configuration
public class SeguridadConfig {

	@Bean
	SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
		return http
				// Sin excepciones. La autorización se agota en "estar
				// autenticado" porque el perfil es uno solo (FR-036d).
				.authorizeHttpRequests(peticiones -> peticiones.anyRequest().authenticated())
				// Autenticación por petición, sin sesión ni cookie. Sin cookie
				// no hay superficie de CSRF, que por eso queda deshabilitado:
				// no es una concesión, es que no hay nada que falsificar.
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.httpBasic(basic -> {
				})
				.build();
	}

	/**
	 * Proveedor contra Active Directory.
	 *
	 * <p>Condicionado a que la URL del directorio esté configurada, y sin
	 * ninguna alternativa detrás. Si falta la propiedad, no queda ningún
	 * proveedor de autenticación y nadie puede entrar: el sistema falla cerrado.
	 * Un proveedor de reserva —usuarios en memoria, en base o en un archivo—
	 * sería una puerta permanente sobre datos de salud, así que no existe.
	 *
	 * <p>La URL debe ser {@code ldaps://}: {@code ldap://} manda la contraseña
	 * del operario en claro por la red.
	 */
	@Bean
	@ConditionalOnProperty("app.directorio.url")
	AuthenticationProvider proveedorActiveDirectory(
			@org.springframework.beans.factory.annotation.Value("${app.directorio.dominio}") String dominio,
			@org.springframework.beans.factory.annotation.Value("${app.directorio.url}") String url) {

		if (!url.startsWith("ldaps://")) {
			throw new IllegalStateException(
					"app.directorio.url tiene que ser ldaps://. Con ldap:// la contraseña del "
							+ "operario viaja en claro.");
		}
		var proveedor = new ActiveDirectoryLdapAuthenticationProvider(dominio, url);
		// El detalle de por qué falló una autenticación no se le devuelve al
		// cliente: distingue "usuario inexistente" de "clave incorrecta" y eso
		// es información sobre el padrón de personal.
		proveedor.setConvertSubErrorCodesToExceptions(false);
		return proveedor;
	}
}
