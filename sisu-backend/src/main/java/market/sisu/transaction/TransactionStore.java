package market.sisu.transaction;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Package-private Panache access to the {@code transactions} collection. Everything outside this
 * package goes through {@link TransactionRepository}, which exposes no update or delete.
 */
@ApplicationScoped
class TransactionStore implements PanacheMongoRepository<Transaction> {}
