package com.offlinepay.feature.scanner.di

import com.offlinepay.feature.scanner.parser.BharatQrParser
import com.offlinepay.feature.scanner.parser.NonUpiContentClassifier
import com.offlinepay.feature.scanner.parser.QrParser
import com.offlinepay.feature.scanner.parser.StandardUpiUriParser
import com.offlinepay.feature.scanner.parser.UpiIntentUriParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module that contributes all [QrParser] implementations into a `Set<QrParser>`.
 *
 * The pipeline sorts parsers by [QrParser.priority] and delegates to the first
 * whose [QrParser.canParse] returns true.
 *
 * Design reference: Section 5.1 (Scanner Pipeline — parser multibinding)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerParserModule {

    @Binds
    @IntoSet
    abstract fun bindBharatQrParser(p: BharatQrParser): QrParser

    @Binds
    @IntoSet
    abstract fun bindUpiIntentUriParser(p: UpiIntentUriParser): QrParser

    @Binds
    @IntoSet
    abstract fun bindStandardUpiUriParser(p: StandardUpiUriParser): QrParser

    @Binds
    @IntoSet
    abstract fun bindNonUpiContentClassifier(p: NonUpiContentClassifier): QrParser
}
