/*
 * Copyright 2020 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.security

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsLoader
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils

import static com.amazonaws.regions.Regions.*

class DefaultAmazonAccountsSynchronizer implements AmazonAccountsSynchronizer {

  List<? extends NetflixAmazonCredentials> synchronize(
    CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
    CredentialsConfig credentialsConfig,
    AccountCredentialsRepository accountCredentialsRepository,
    DefaultAccountConfigurationProperties defaultAccountConfigurationProperties,
    CatsModule catsModule) {
    if (!credentialsConfig.accounts && !credentialsConfig.defaultAssumeRole) {
      def defaultEnvironment = defaultAccountConfigurationProperties.environment ?: defaultAccountConfigurationProperties.env
      def defaultAccountType = defaultAccountConfigurationProperties.accountType ?: defaultAccountConfigurationProperties.env
      credentialsConfig.accounts = [new CredentialsConfig.Account(name: defaultAccountConfigurationProperties.env, environment: defaultEnvironment, accountType: defaultAccountType)]
      if (!credentialsConfig.defaultRegions) {
        credentialsConfig.defaultRegions = [US_EAST_1, US_WEST_1, US_WEST_2, EU_WEST_1].collect {
          new CredentialsConfig.Region(name: it.name)
        }
      }
    }

    List<? extends NetflixAmazonCredentials> accounts = credentialsLoader.load(credentialsConfig)

    def (ArrayList<NetflixAmazonCredentials> accountsToAdd, List<String> namesOfDeletedAccounts) =
    ProviderUtils.calculateAccountDeltas(accountCredentialsRepository, NetflixAmazonCredentials, accounts)

    accountsToAdd.each { NetflixAmazonCredentials account ->
      accountCredentialsRepository.save(account.name, account)
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    accountCredentialsRepository.all.findAll {
      it instanceof NetflixAmazonCredentials
    } as List<NetflixAmazonCredentials>
  }

}
