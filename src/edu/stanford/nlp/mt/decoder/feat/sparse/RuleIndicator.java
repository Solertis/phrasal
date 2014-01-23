package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.SourceClassMap;
import edu.stanford.nlp.mt.base.TargetClassMap;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Indicator features for each rule in a derivation.
 * 
 * @author Daniel Cer
 * @author Spence Green
 * 
 */
public class RuleIndicator implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "DPT";

  private static final int DEFAULT_LEXICAL_CUTOFF = 50;
  
  private final boolean addLexicalizedRule;
  private final boolean addClassBasedRule;
  private final int countFeatureIndex;
  private final int lexicalCutoff;
  private final boolean addDomainFeatures;

  private Map<Integer,Pair<String,Integer>> sourceIdInfoMap;
  private SourceClassMap sourceMap;
  private TargetClassMap targetMap;
  
  /**
   * Constructor.
   */
  public RuleIndicator() {
    this.addLexicalizedRule = true;
    this.addClassBasedRule = false;
    this.countFeatureIndex = -1;
    this.lexicalCutoff = DEFAULT_LEXICAL_CUTOFF;
    this.addDomainFeatures = false;
  }

  /**
   * Constructor for reflection loading.
   * 
   * @param args
   */
  public RuleIndicator(String... args) {
    Properties options = SparseFeatureUtils.argsToProperties(args);
    this.addLexicalizedRule = options.containsKey("addLexicalized");
    this.addClassBasedRule = options.contains("addClassBased");
    this.countFeatureIndex = PropertiesUtils.getInt(options, "countFeatureIndex", -1);
    if (addClassBasedRule) {
      sourceMap = SourceClassMap.getInstance();
      targetMap = TargetClassMap.getInstance();
    }
    this.addDomainFeatures = options.containsKey("domainFile");
    if (addDomainFeatures) {
      sourceIdInfoMap = SparseFeatureUtils.loadGenreFile(options.getProperty("domainFile"));
    }
    this.lexicalCutoff = PropertiesUtils.getInt(options, "lexicalCutoff", DEFAULT_LEXICAL_CUTOFF);
  }

  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    Pair<String,Integer> genreInfo = addDomainFeatures && sourceIdInfoMap.containsKey(f.sourceInputId) ? 
        sourceIdInfoMap.get(f.sourceInputId) : null;
    final String genre = genreInfo == null ? null : genreInfo.first();
    
    if (addLexicalizedRule && aboveThreshold(f.rule)) {
      String sourcePhrase = f.sourcePhrase.toString("-");
      String targetPhrase = f.targetPhrase.toString("-");
      String featureString = FEATURE_NAME + ":" + String.format("%s>%s", sourcePhrase, targetPhrase);
      features.add(new FeatureValue<String>(featureString, 1.0));
      if (genre != null) {
        features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
      }
    }
    if (addClassBasedRule) {
      StringBuilder sb = new StringBuilder();
      for (IString token : f.sourcePhrase) {
        if (sb.length() > 0) sb.append("-");
        String tokenClass = sourceMap.get(token).toString();
        sb.append(tokenClass);
      }
      sb.append(">");
      boolean seenFirst = false;
      for (IString token : f.targetPhrase) {
        if (seenFirst) sb.append("-");
        String tokenClass = targetMap.get(token).toString();
        sb.append(tokenClass);
        seenFirst = true;
      }
      String featureString = FEATURE_NAME + ":" + sb.toString();
      features.add(new FeatureValue<String>(featureString, 1.0));
      if (genre != null) {
        features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
      }
    }
    return features;
  }

  private boolean aboveThreshold(ConcreteRule<IString, String> rule) {
    if (countFeatureIndex < 0) return true;
    if (countFeatureIndex >= rule.abstractRule.scores.length) {
      // Generated by unknown word model...don't know count.
      return false;
    }
    int count = (int) Math.round(Math.exp(rule.abstractRule.scores[countFeatureIndex]));
    return count > lexicalCutoff;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
