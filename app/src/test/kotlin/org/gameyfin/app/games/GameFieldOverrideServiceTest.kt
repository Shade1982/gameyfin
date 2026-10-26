package org.gameyfin.app.games

import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import org.gameyfin.app.games.overrides.FieldSource
import org.gameyfin.app.games.overrides.GameFieldOverrideRepository
import org.gameyfin.app.games.overrides.GameFieldOverrideService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource
import kotlin.test.assertEquals

@SpringJUnitConfig(GameFieldOverrideServiceTest.Config::class)
class GameFieldOverrideServiceTest {
    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `replace should store, overwrite and clear the overrides of a single game`() {
        val entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory)
        val repository = JpaRepositoryFactory(entityManager).getRepository(GameFieldOverrideRepository::class.java)
        val service = GameFieldOverrideService(repository)
        val tx = TransactionTemplate(transactionManager)

        tx.executeWithoutResult {
            service.replace(1, mapOf("title" to FieldSource("steam", false), "summary" to FieldSource("igdb", true)))
            service.replace(2, mapOf("title" to FieldSource("grid", false)))
        }
        assertEquals(
            mapOf("title" to FieldSource("steam", false), "summary" to FieldSource("igdb", true)),
            tx.execute { service.get(1) }
        )

        tx.executeWithoutResult { service.replace(1, mapOf("title" to FieldSource("grid", false))) }
        assertEquals(mapOf("title" to FieldSource("grid", false)), tx.execute { service.get(1) })
        assertEquals(mapOf("title" to FieldSource("grid", false)), tx.execute { service.get(2) })

        tx.executeWithoutResult { service.replace(1, emptyMap()) }
        assertEquals(emptyMap(), tx.execute { service.get(1) })
        assertEquals(1, tx.execute { service.get(2) }!!.size)
    }

    @Configuration
    class Config {
        @Bean(destroyMethod = "close")
        fun dataSource() = HikariDataSource().apply {
            jdbcUrl = "jdbc:h2:mem:game-overrides;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
        }

        @Bean
        fun entityManagerFactory(dataSource: DataSource) = LocalContainerEntityManagerFactoryBean().apply {
            setDataSource(dataSource)
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setPackagesToScan("org.gameyfin.app")
            setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "create-drop"))
        }

        @Bean
        fun transactionManager(entityManagerFactory: EntityManagerFactory): PlatformTransactionManager =
            JpaTransactionManager(entityManagerFactory)
    }
}
