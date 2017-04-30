/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package third.apache.lucene.analysis.hi;


import java.io.IOException;
import java.io.Reader;

import third.apache.lucene.analysis.TokenStream;
import third.apache.lucene.analysis.Tokenizer;
import third.apache.lucene.analysis.core.DecimalDigitFilter;
import third.apache.lucene.analysis.core.LowerCaseFilter;
import third.apache.lucene.analysis.core.StopFilter;
import third.apache.lucene.analysis.in.IndicNormalizationFilter;
import third.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import third.apache.lucene.analysis.standard.StandardTokenizer;
import third.apache.lucene.analysis.standard.std40.StandardTokenizer40;
import third.apache.lucene.analysis.util.CharArraySet;
import third.apache.lucene.analysis.util.StopwordAnalyzerBase;
import third.apache.lucene.util.Version;

/**
 * Analyzer for Hindi.
 */
public final class HindiAnalyzer extends StopwordAnalyzerBase {
  private final CharArraySet stemExclusionSet;
  
  /**
   * File containing default Hindi stopwords.
   * 
   * Default stopword list is from http://members.unine.ch/jacques.savoy/clef/index.html
   * The stopword list is BSD-Licensed.
   */
  public final static String DEFAULT_STOPWORD_FILE = "stopwords.txt";
  private static final String STOPWORDS_COMMENT = "#";
  
  /**
   * Returns an unmodifiable instance of the default stop-words set.
   * @return an unmodifiable instance of the default stop-words set.
   */
  public static CharArraySet getDefaultStopSet(){
    return DefaultSetHolder.DEFAULT_STOP_SET;
  }
  
  /**
   * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer class 
   * accesses the static final set the first time.;
   */
  private static class DefaultSetHolder {
    static final CharArraySet DEFAULT_STOP_SET;

    static {
      try {
        DEFAULT_STOP_SET = loadStopwordSet(false, HindiAnalyzer.class, DEFAULT_STOPWORD_FILE, STOPWORDS_COMMENT);
      } catch (IOException ex) {
        // default set should always be present as it is part of the
        // distribution (JAR)
        throw new RuntimeException("Unable to load default stopword set");
      }
    }
  }
  
  /**
   * Builds an analyzer with the given stop words
   * 
   * @param stopwords a stopword set
   * @param stemExclusionSet a stemming exclusion set
   */
  public HindiAnalyzer(CharArraySet stopwords, CharArraySet stemExclusionSet) {
    super(stopwords);
    this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet));
  }
  
  /**
   * Builds an analyzer with the given stop words 
   * 
   * @param stopwords a stopword set
   */
  public HindiAnalyzer(CharArraySet stopwords) {
    this(stopwords, CharArraySet.EMPTY_SET);
  }
  
  /**
   * Builds an analyzer with the default stop words:
   * {@link #DEFAULT_STOPWORD_FILE}.
   */
  public HindiAnalyzer() {
    this(DefaultSetHolder.DEFAULT_STOP_SET);
  }

  /**
   * Creates
   * {@link third.apache.lucene.analysis.Analyzer.TokenStreamComponents}
   * used to tokenize all the text in the provided {@link Reader}.
   * 
   * @return {@link third.apache.lucene.analysis.Analyzer.TokenStreamComponents}
   *         built from a {@link StandardTokenizer} filtered with
   *         {@link LowerCaseFilter}, {@link DecimalDigitFilter}, {@link IndicNormalizationFilter},
   *         {@link HindiNormalizationFilter}, {@link SetKeywordMarkerFilter}
   *         if a stem exclusion set is provided, {@link HindiStemFilter}, and
   *         Hindi Stop words
   */
  @Override
  protected TokenStreamComponents createComponents(String fieldName) {
    final Tokenizer source;
    if (getVersion().onOrAfter(Version.LUCENE_4_7_0)) {
      source = new StandardTokenizer();
    } else {
      source = new StandardTokenizer40();
    }
    TokenStream result = new LowerCaseFilter(source);
    if (getVersion().onOrAfter(Version.LUCENE_5_4_0)) {
      result = new DecimalDigitFilter(result);
    }
    if (!stemExclusionSet.isEmpty())
      result = new SetKeywordMarkerFilter(result, stemExclusionSet);
    result = new IndicNormalizationFilter(result);
    result = new HindiNormalizationFilter(result);
    result = new StopFilter(result, stopwords);
    result = new HindiStemFilter(result);
    return new TokenStreamComponents(source, result);
  }
}
