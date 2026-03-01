package hu.imregergo.paymentservice.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class TxConfig {

    @Bean
    @Qualifier("requiresNewTxTemplate")
    public TransactionTemplate requiresNewTxTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        return tt;
    }
}
