package com.offlinepay.feature.scanner

import com.offlinepay.core.domain.usecase.qr.ParseQrCodeUseCase
import com.offlinepay.feature.scanner.parser.QrParserPipeline
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the scanner feature.
 *
 * Binds the [QrParserPipeline] concrete implementation to the [ParseQrCodeUseCase] interface
 * so that the pipeline is injectable wherever [ParseQrCodeUseCase] is required.
 *
 * Design reference: Section 5.1 (Scanner Pipeline), Section 3.3 (ParseQrCodeUseCase)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerModule {

    @Binds
    @Singleton
    abstract fun bindParseQrCodeUseCase(
        pipeline: QrParserPipeline,
    ): ParseQrCodeUseCase
}
