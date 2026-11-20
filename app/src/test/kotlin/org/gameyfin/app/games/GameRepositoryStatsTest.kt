package org.gameyfin.app.games

import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameFieldMetadata
import org.gameyfin.app.games.entities.GameFieldPluginSource
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.entities.Library
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
import java.nio.file.Path
import javax.sql.DataSource
import kotlin.test.assertEquals

@SpringJUnitConfig(GameRepositoryStatsTest.Config::class)
class GameRepositoryStatsTest {
    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `countFieldsBySourcePlugin should count fields per plugin and field name`() {
        val entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory)
        val repository = JpaRepositoryFactory(entityManager).getRepository(GameRepository::class.java)
        val tx = TransactionTemplate(transactionManager)

        tx.executeWithoutResult {
            val steam = entityManager.merge(PluginManagementEntry("steam", priority = 5))
            val grid = entityManager.merge(PluginManagementEntry("grid", priority = 1))
            val library = Library(name = "Test")
            entityManager.persist(library)

            repeat(2) { i ->
                val game = Game(Path.of("/games/$i"), library)
                game.metadata.fields = mutableMapOf(
                    "title" to GameFieldMetadata(source = GameFieldPluginSource(steam)),
                    "headerImage" to GameFieldMetadata(source = GameFieldPluginSource(grid))
                )
                entityManager.persist(game)
            }
        }

        val stats = tx.execute { repository.countFieldsBySourcePlugin() }!!
            .associate { "${it[0]}:${it[1]}" to (it[2] as Number).toLong() }

        assertEquals(mapOf("steam:title" to 2L, "grid:headerImage" to 2L), stats)
    }

    @Configuration
    class Config {
        @Bean(destroyMethod = "close")
        fun dataSource() = HikariDataSource().apply {
            jdbcUrl = "jdbc:h2:mem:game-stats;DB_CLOSE_DELAY=-1"
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
