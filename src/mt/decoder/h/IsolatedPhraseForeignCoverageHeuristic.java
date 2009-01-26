package mt.decoder.h;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.CoverageSet;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.decoder.feat.IsolatedPhraseFeaturizer;
import mt.decoder.util.Hypothesis;
import mt.decoder.util.Scorer;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class IsolatedPhraseForeignCoverageHeuristic<TK, FV> implements SearchHeuristic<TK, FV> {
	public static final String DEBUG_PROPERTY = "ipfcHeuristicDebug";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
	final IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer;
	final Scorer<FV> scorer;
	
	public SearchHeuristic<TK,FV> clone() {
	   try {
	  	 return (SearchHeuristic<TK,FV>) super.clone();
	   } catch (CloneNotSupportedException e) { return null; /* wnh */ }
	}
	
	
	/**
	 * 
	 * @param phraseFeaturizer
	 * @param scorer
	 */
	public IsolatedPhraseForeignCoverageHeuristic(IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer) {
		this.phraseFeaturizer = phraseFeaturizer;
		this.scorer = scorer;
	}
	
	@Override
	public double getHeuristicDelta(Hypothesis<TK, FV> newHypothesis,
			CoverageSet newCoverage) {
		double oldH = newHypothesis.preceedingHyp.h;
		double newH = 0;
		CoverageSet coverage = newHypothesis.foreignCoverage;
		int startEdge = coverage.nextClearBit(0);
		
		//System.out.printf("getHeuristicDelta:\n");
		//System.out.printf("coverage: %s", newHypothesis.foreignCoverage);
		
		int foreignSize = newHypothesis.foreignSequence.size();
		for (int endEdge; startEdge < foreignSize; startEdge = coverage.nextClearBit(endEdge)) {
			endEdge = coverage.nextSetBit(startEdge);
			
			if (endEdge == -1) {
				endEdge = newHypothesis.foreignSequence.size();
			}
			double localH = hSpanScores.getScore(startEdge, endEdge-1);
			
			//System.out.printf("retreiving score for %d:%d ==> %f", startEdge, endEdge-1, localH);
			
			newH += localH;
		}
		return newH - oldH;
	}

	private SpanScores hSpanScores;
	
	@Override
	public double getInitialHeuristic(Sequence<TK> foreignSequence,
			List<ConcreteTranslationOption<TK>> options, int translationId) {
		
		int foreignSequenceSize = foreignSequence.size();
		
		SpanScores viterbiSpanScores = new SpanScores(foreignSequenceSize);
		
		if (DEBUG) {
			System.err.println("IsolatedPhraseForeignCoverageHeuristic");
			System.err.printf("Foreign Sentence: %s\n", foreignSequence);
			
			System.err.println("Initial Spans from PhraseTable");
			System.err.println("------------------------------");
		}
		
		// initialize viterbiSpanScores
		for (ConcreteTranslationOption<TK> option : options) {
			Featurizable<TK, FV> f = new Featurizable<TK, FV>(foreignSequence, option, translationId); 
			List<FeatureValue<FV>> phraseFeatures = phraseFeaturizer.phraseListFeaturize(f);
			double score = scorer.getIncrementalScore(phraseFeatures);
			int terminalPos = option.foreignPos + option.abstractOption.foreign.size()-1;
			if (score > viterbiSpanScores.getScore(option.foreignPos, terminalPos)) {
				viterbiSpanScores.setScore(option.foreignPos, terminalPos, score);
			}
			if (DEBUG) {
				System.err.printf("\t%d:%d %s->%s score: %.3f\n", option.foreignPos, terminalPos, option.abstractOption.foreign, option.abstractOption.translation, score);
				System.err.printf("\t\tFeatures: %s\n", phraseFeatures);
			}
		}
		
		if (DEBUG) {
			System.err.println("Initial Minimums");
			System.err.println("------------------------------");
		
			for (int startPos = 0; startPos < foreignSequenceSize; startPos++) {
				for (int endPos = 0; endPos < foreignSequenceSize; endPos++) {
					System.err.printf("\t%d:%d score: %f\n", startPos, endPos, viterbiSpanScores.getScore(startPos, endPos));
				}	
			}
		}
		
		
		
		if (DEBUG) {
			System.err.println();
			System.err.println("Merging span scores");
			System.err.println("-------------------");
		}
		
		// Viterbi combination of spans
		for (int spanSize = 2; spanSize <= foreignSequenceSize; spanSize++) {
			if (DEBUG) {
				System.err.printf("\n* Merging span size: %d\n", spanSize);
			}
			for (int startPos = 0; startPos <= foreignSequenceSize-spanSize; startPos++) {
				int terminalPos = startPos + spanSize-1;
				double bestScore = viterbiSpanScores.getScore(startPos, terminalPos);
				for (int centerEdge = startPos+1; centerEdge <= terminalPos; centerEdge++) {
					double combinedScore = viterbiSpanScores.getScore(startPos, centerEdge-1) +
										   viterbiSpanScores.getScore(centerEdge, terminalPos);
					if (combinedScore > bestScore) {
						if (DEBUG) {
							System.err.printf("\t%d:%d updating to %.3f from %.3f\n", startPos, terminalPos, combinedScore, bestScore);
						}
						bestScore = combinedScore;
					}
				}
				viterbiSpanScores.setScore(startPos, terminalPos, bestScore);
			}
		}
		
		if (DEBUG) {
			System.err.println();
			System.err.println("Final Scores");
			System.err.println("------------");
			for (int startEdge = 0; startEdge < foreignSequenceSize; startEdge++) {
				for (int terminalEdge = startEdge; terminalEdge < foreignSequenceSize; terminalEdge++) {
					System.err.printf("\t%d:%d score: %.3f\n", startEdge, terminalEdge, viterbiSpanScores.getScore(startEdge, terminalEdge));
				}
			}
		}
		
		hSpanScores = viterbiSpanScores;
		
		double hCompleteSequence = hSpanScores.getScore(0, foreignSequenceSize-1); 
		if (DEBUG) {
			System.err.println("Done IsolatedForeignCoverageHeuristic");
		}
		return hCompleteSequence;
	}
		
	private class SpanScores {
		final double[] spanValues;
		final int terminalPositions;
		public SpanScores(int length) {
			terminalPositions = length+1;			
			spanValues = new double[terminalPositions*terminalPositions];
			Arrays.fill(spanValues, Double.NEGATIVE_INFINITY);
		}
		
		public double getScore(int startPosition, int endPosition) {
			return spanValues[startPosition*terminalPositions + endPosition];
		}
		
		public void setScore(int startPosition, int endPosition, double score) {
			spanValues[startPosition*terminalPositions + endPosition] = score;
		}
	}
}
