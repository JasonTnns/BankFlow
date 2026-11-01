package com.example.bankflow.core.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [InfrastructureModule::class])
@ComponentScan("com.example.bankflow")
class AppModule