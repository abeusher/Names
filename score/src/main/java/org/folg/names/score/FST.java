/*
 * Copyright 2011 Foundation for On-Line Genealogy, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.folg.names.score;

import java.util.logging.Logger;

/**
 * A (type of) finite state transducer
 * This isn't a regular finite state transducer.  It's specific to the matching problem at hand.
 * It's really just a 2D table, with a row for each letter+1 in the source name and a column for each column
 * in the target name.  It's assumed that arcs exist between each cell and its neighbors up, right, and
 * diagonally up and right.
 *
 * Read the comment in updateScore for an important note
 */
public class FST {
   private static Logger logger = Logger.getLogger("org.folg.names.score");

   // holds the best getScore for each node
   private int[][] score;
   // holds the X position of the node yielding the best getScore so you can walk the best path backwards
   private int[][] prevX;
   // holds the Y position of the node yielding the best getScore so you can walk the best path backwards
   private int[][] prevY;
   // source tokens
   private int[] source;
   // target tokens
   private int[] target;

   /**
    * Create a finite state transducer.
    */
   public FST(int[] sourceTokens, int[] targetTokens) {
      this.source = sourceTokens;
      this.target = targetTokens;
      score = new int[source.length+1][target.length+1];
      prevX = new int[source.length+1][target.length+1];
      prevY = new int[source.length+1][target.length+1];
      reset();
   }

   public void reset() {
      for (int x = 0; x < source.length+1; x++) {
         for (int y = 0; y < target.length+1; y++) {
            score[x][y] = Integer.MAX_VALUE;
            prevX[x][y] = -1;
            prevY[x][y] = -1;
         }
      }
      score[0][0] = 0;
   }

   // update score for a node in the graph
   private void updateScore(int fromX, int fromY, int toX, int toY, int newScore) {
      // If you're reading through this file, the following comment is important:

      // Normally, viterbi simply compares newScore < oldScore to compute the lowest-cost path.
      // But in getBestPathScore we weight earlier edit costs more than later edit costs.
      // So ideally, we would modify viterbi accordingly, by multiplying the score based upon the positional weight.
      // But this would introduce floating-point arithmetic, which I want to avoid for performance reasons.

      // So instead, the following code simply tests if the new cost is equal to the old cost, and if it is,
      // it replaces the old cost with the new cost if the cost leading to the new cost is less than
      // the cost leading to the old cost.

      // In tests on millions of name pairs, this approach does not produce the minimum cost about 1:10,000 times.
      // That is, calling getBestPathScore on FST(source,target) != getBestPathScore on FST(target,source) about once
      // every 10,000 pairs.
      // If you want to guarantee the minimum cost, create both FST's and take the minimum getBestPathScore.
      // That's what FeaturesGenerator does

      // If someone can figure out how to modify the viterbi algorithm to take positional weight into account
      // without introducing floating-point arithmetic, let me know.

      int oldScore = score[toX][toY];
      if (newScore < oldScore ||
          (newScore == oldScore && score[fromX][fromY] < score[prevX[toX][toY]][prevY[toX][toY]])) {
         score[toX][toY] = newScore;
         prevX[toX][toY] = fromX;
         prevY[toX][toY] = fromY;
      }
   }

   /**
    * Compute the best path to transform source into target given a set of weighted edits (use Viterbi)
    * @param we WeightedEdits on which to compute best path
    */
   public void computeBestPath(WeightedEdits we) {
      for (int x = 0; x <= source.length; x++) {
         for (int y = 0; y <= target.length; y++) {
            int curScore = score[x][y];

            // calc getScore for empty/targetChar edit
            if (y < target.length) {
               updateScore(x, y, x, y+1, curScore + we.getCost(WeightedEditDistanceTokenizer.EMPTY_TOKEN, target[y]));
            }

            // calc getScore for sourceChar/empty edit
            if (x < source.length) {
               updateScore(x, y, x+1, y, curScore + we.getCost(source[x], WeightedEditDistanceTokenizer.EMPTY_TOKEN));
            }

            // calc getScore for sourceChar/targetChar edit
            if (x < source.length && y < target.length) {
               updateScore(x, y, x+1, y+1, curScore + we.getCost(source[x], target[y]));
            }
         }
      }
   }

   private int getToken(int[] tokens, int from, int to) {
      return (from == to ? WeightedEditDistanceTokenizer.EMPTY_TOKEN : tokens[from]);
   }

   private double getMinCost(int[] tokens, WeightedEdits we, double smooth) {
      double cost = 0;
      for (int i = 0; i < tokens.length; i++) {
         cost += (we.getCost(tokens[i],tokens[i]) * 1.0) / (i+smooth);
      }
      return cost;
   }

   /**
    * Return a normalized score 0..1.  Call after computeBestPath.
    * @param we specified WeightedEdits
    * @return normalized score 0..1
    */
   public double getBestPathScore(WeightedEdits we, double smooth) {
      int idx = 0;
      // start from the end
      int toX = source.length;
      int toY = target.length;
      int[] scoreBuffer = new int[toX+toY];
      while (toX > 0 || toY > 0) {
         int fromX = prevX[toX][toY];
         int fromY = prevY[toX][toY];
         int cost = we.getCost(getToken(source, fromX, toX), getToken(target, fromY, toY));
         scoreBuffer[idx++] = cost;
         //logger.info("["+getToken(source,fromX,toX)+","+getToken(target,fromY,toY)+"]="+cost);
         toX = fromX;
         toY = fromY;
      }

      // return normalized score
      double sourceCost = getMinCost(source, we, smooth);
      double targetCost = getMinCost(target, we, smooth);
      double totalCost = 0;
      int idx1 = idx - 1;
      for (int i = 0; i < idx; i++) {
         totalCost += (scoreBuffer[idx1 - i] * 1.0) / (i+smooth);  // weight earlier differences higher than later differences
      }
      return totalCost / Math.max(sourceCost, targetCost);
   }

   /**
    * Update counts in the specified WeightedEdits.  Call after computeBestPath during training.
    * @param we specified WeightedEdits
    */
   public void updateCounts(WeightedEdits we) {
      // update counts by following best path backwards
      int toX = source.length;
      int toY = target.length;
      while (toX > 0 || toY > 0) {
         int fromX = prevX[toX][toY];
         int fromY = prevY[toX][toY];
         we.addCount(getToken(source, fromX, toX), getToken(target, fromY, toY), 1);
         toX = fromX;
         toY = fromY;
      }
   }
}
