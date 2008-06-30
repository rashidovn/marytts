/**
 * Copyright 2007 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * Permission is hereby granted, free of charge, to use and distribute
 * this software and its documentation without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of this work, and to
 * permit persons to whom this work is furnished to do so, subject to
 * the following conditions:
 * 
 * 1. The code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 * 2. Any modifications must be clearly marked as such.
 * 3. Original authors' names are not deleted.
 * 4. The authors' names are not used to endorse or promote products
 *    derived from this software without specific prior written
 *    permission.
 *
 * DFKI GMBH AND THE CONTRIBUTORS TO THIS WORK DISCLAIM ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL DFKI GMBH NOR THE
 * CONTRIBUTORS BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 */

package marytts.signalproc.adaptation.codebook;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import javax.sound.sampled.UnsupportedAudioFileException;

import marytts.signalproc.adaptation.AdaptationUtils;
import marytts.signalproc.adaptation.BaselineAdaptationItem;
import marytts.signalproc.adaptation.BaselineAdaptationSet;
import marytts.signalproc.adaptation.BaselineFeatureExtractor;
import marytts.signalproc.adaptation.BaselinePreprocessor;
import marytts.signalproc.adaptation.IndexMap;
import marytts.signalproc.adaptation.outlier.KMeansMappingEliminatorParams;
import marytts.signalproc.adaptation.outlier.TotalStandardDeviations;
import marytts.signalproc.adaptation.prosody.PitchMappingFile;
import marytts.signalproc.adaptation.prosody.PitchTrainer;
import marytts.signalproc.analysis.Lsfs;
import marytts.signalproc.util.distance.DistanceComputer;
import marytts.signalproc.window.Window;
import marytts.tools.voiceimport.BasenameList;
import marytts.util.FileUtils;
import marytts.util.MaryRandomAccessFile;
import marytts.util.StringUtils;


/**
 * @author oytun.turk
 *
 * This class implements training for weighted codebook mapping based voice conversion
 * using parallel training data (i.e. source and target data in pairs of audio recordings which have identical content)
 */
public class WeightedCodebookParallelTrainer extends WeightedCodebookTrainer {
    
    public WeightedCodebookParallelTrainer(BaselinePreprocessor pp,
                                           BaselineFeatureExtractor fe,
                                           WeightedCodebookTrainerParams pa) 
    {
        super(pp, fe, pa);
    }
    
    //Call this function after initializing the trainer to perform training
    public void run() throws IOException, UnsupportedAudioFileException
    {
        if (checkParams())
        {
            BaselineAdaptationSet sourceTrainingSet = new BaselineAdaptationSet(wcParams.sourceTrainingFolder);
            BaselineAdaptationSet targetTrainingSet = new BaselineAdaptationSet(wcParams.targetTrainingFolder);

            train(sourceTrainingSet, targetTrainingSet);
        }
    }
      
    //Parallel training
    public void train(BaselineAdaptationSet sourceTrainingSet, BaselineAdaptationSet targetTrainingSet) throws IOException, UnsupportedAudioFileException
    {
        int [] map = getIndexedMapping(sourceTrainingSet, targetTrainingSet); 
        
        train(sourceTrainingSet, targetTrainingSet, map);
    }
    
    public static void main(String[] args) throws UnsupportedAudioFileException, IOException
    {
        mainAngryF();
        
        mainHappyF();

        mainSadF();
        
        //mainSadF();
        
        //mainSadLG();
        
        //mainAngryLG();
        
        //mainHappyLG();
    }
    
    public static void mainAngryF() throws UnsupportedAudioFileException, IOException
    {
        BaselinePreprocessor pp = new BaselinePreprocessor();
        BaselineFeatureExtractor fe = new BaselineFeatureExtractor();
        
        WeightedCodebookTrainerParams pa = new WeightedCodebookTrainerParams();
        
        pa.codebookHeader.codebookType = WeightedCodebookFileHeader.FRAMES; //Frame-by-frame mapping of features
        //pa.codebookHeader.codebookType = WeightedCodebookFileHeader.FRAME_GROUPS; pa.codebookHeader.numNeighboursInFrameGroups = 3; //Mapping of frame average features (no label information but fixed amount of neighbouring frames is used)
        //pa.codebookHeader.codebookType = WeightedCodebookFileHeader.LABELS; //Mapping of label average features
        //pa.codebookHeader.codebookType = WeightedCodebookFileHeader.LABEL_GROUPS; pa.codebookHeader.numNeighboursInLabelGroups = 1; //Mapping of average features collected across label groups (i.e. vowels, consonants, etc)
        //pa.codebookHeader.codebookType = WeightedCodebookFileHeader.SPEECH; //Mapping of average features collected across all speech parts (i.e. like spectral equalization)

        pa.codebookHeader.sourceTag = "neutralF"; //Source name tag (i.e. style or speaker identity)
        pa.codebookHeader.targetTag = "angryF"; //Target name tag (i.e. style or speaker identity)
        
        pa.trainingBaseFolder = "D:\\Oytun\\DFKI\\voices\\Interspeech08_out\\neutral2angry"; //Training base directory
        pa.sourceTrainingFolder = "D:\\Oytun\\DFKI\\voices\\Interspeech08\\neutral\\train_200"; //Source training folder
        pa.targetTrainingFolder = "D:\\Oytun\\DFKI\\voices\\Interspeech08\\angry\\train_200"; //Target training folder

        pa.indexMapFileExtension = ".imf"; //Index map file extensions
        
        pa.codebookHeader.lsfParams.lpOrder = 20; //Auto set
        pa.codebookHeader.lsfParams.preCoef = 0.97f;
        pa.codebookHeader.lsfParams.skipsize = 0.010f;
        pa.codebookHeader.lsfParams.winsize = 0.020f;
        pa.codebookHeader.lsfParams.windowType = Window.HAMMING;
        
        String baseFile = StringUtils.checkLastSlash(pa.trainingBaseFolder) + pa.codebookHeader.sourceTag + "_X_" + pa.codebookHeader.targetTag;
        pa.codebookFile = baseFile + "_200" + WeightedCodebookFile.DEFAULT_EXTENSION;
        pa.pitchMappingFile = baseFile + "_200" + PitchMappingFile.DEFAULT_EXTENSION;
        
        pa.isForcedAnalysis = false;
        
        pa.codebookHeader.ptcParams.ws = 0.040;
        pa.codebookHeader.ptcParams.ss = 0.005;
        pa.codebookHeader.ptcParams.voicingThreshold = 0.30;
        pa.codebookHeader.ptcParams.isDoublingCheck = false;
        pa.codebookHeader.ptcParams.isHalvingCheck = false;
        pa.codebookHeader.ptcParams.minimumF0 = 40.0f;
        pa.codebookHeader.ptcParams.maximumF0 = 400.0f;
        pa.codebookHeader.ptcParams.centerClippingRatio = 0.3;
        pa.codebookHeader.ptcParams.cutOff1 = pa.codebookHeader.ptcParams.minimumF0-20.0;
        pa.codebookHeader.ptcParams.cutOff2 = pa.codebookHeader.ptcParams.maximumF0+200.0;
        
        pa.codebookHeader.energyParams.windowSizeInSeconds = 0.020;
        pa.codebookHeader.energyParams.skipSizeInSeconds = 0.010;
        
        TotalStandardDeviations tsd = new TotalStandardDeviations();
        tsd.lsf = 1.5;
        tsd.f0 = 1.0;
        tsd.duration = 1.0;
        tsd.energy = 2.0;
        
        //Gaussian outlier eliminator
        //Decreasing totalStandardDeviations will lead to more outlier eliminations, i.e. smaller codebooks
        pa.gaussianEliminatorParams.isActive = true; //Set to false if you do not want to use this eliminator at all      
        pa.gaussianEliminatorParams.isCheckLsfOutliers = true;
        pa.gaussianEliminatorParams.isEliminateTooSimilarLsf = true;
        pa.gaussianEliminatorParams.isCheckF0Outliers = true; 
        pa.gaussianEliminatorParams.isCheckDurationOutliers = true;    
        pa.gaussianEliminatorParams.isCheckEnergyOutliers = true;
        pa.gaussianEliminatorParams.totalStandardDeviations = new TotalStandardDeviations(tsd);
        //
        
        //KMeans one-to-many and many-to-one mapping eliminator
        pa.kmeansEliminatorParams.isActive = true; //Set to false if you do not want to use this eliminator at all
        
        //pa.kmeansEliminatorParams.eliminationAlgorithm = KMeansMappingEliminatorParams.ELIMINATE_LEAST_LIKELY_MAPPINGS; 
        //pa.kmeansEliminatorParams.eliminationLikelihood = 0.20;
        
        pa.kmeansEliminatorParams.eliminationAlgorithm = KMeansMappingEliminatorParams.ELIMINATE_MEAN_DISTANCE_MISMATCHES; 
        pa.kmeansEliminatorParams.distanceType = DistanceComputer.NORMALIZED_EUCLIDEAN_DISTANCE;
        //pa.kmeansEliminatorParams.distanceType = DistanceComputer.EUCLIDEAN_DISTANCE;
        pa.kmeansEliminatorParams.isGlobalVariance = true;
        
        //pa.kmeansEliminatorParams.eliminationAlgorithm = KMeansMappingEliminatorParams.ELIMINATE_USING_SUBCLUSTER_MEAN_DISTANCES;
        
        pa.kmeansEliminatorParams.isSeparateClustering = false; //Cluster features separately(true) or together(false)?
        
        //Effective only when isSeparateClustering clustering is false
        tsd.general = 0.1;
        pa.kmeansEliminatorParams.numClusters = 30;
        
        //Effective only when isSeparateClustering clustering is true
        tsd.lsf = 1.0;
        tsd.f0 = 1.0;
        tsd.duration = 1.0;
        tsd.energy = 1.0;
        pa.kmeansEliminatorParams.numClustersLsf = 30;
        pa.kmeansEliminatorParams.numClustersF0 = 50;
        pa.kmeansEliminatorParams.numClustersDuration = 5;
        pa.kmeansEliminatorParams.numClustersEnergy = 5;
        
        pa.kmeansEliminatorParams.isCheckLsfOutliers = true;    
        pa.kmeansEliminatorParams.isCheckF0Outliers = false; 
        pa.kmeansEliminatorParams.isCheckDurationOutliers = false;  
        pa.kmeansEliminatorParams.isCheckEnergyOutliers = false;
        //
        
        pa.kmeansEliminatorParams.totalStandardDeviations = new TotalStandardDeviations(tsd);
        //
        
        WeightedCodebookParallelTrainer t = new WeightedCodebookParallelTrainer(pp, fe, pa);
        
        t.run();
        
        System.out.println("Training completed...");
    }
    
    public static void mainHappyF() throws UnsupportedAudioFileException, IOException
    {
        BaselinePreprocessor pp = new BaselinePreprocessor();
        BaselineFeatureExtractor fe = new BaselineFeatureExtractor();
        
        WeightedCodebookTrainerParams pa = new WeightedCodebookTrainerParams();
        
        pa.codebookHeader.codebookType = WeightedCodebookFileHeader.FRAMES; //Frame-by-frame mapping of features
        //pa.codebookHeader.codebookType = WeightedCodebookFileHeader.FRAME_GROUPS; pa.codebookHeader.numNeighboursInFrameGroups = 3; //Mapping of frame average features (no label information but fixed amount of neighbouring frames is used)
        //pa.codebookHeader.codebookType = WeightedCodebookFileHeader.LABELS; //Mapping of label average features
        //pa.codebookHeader.codebookType = WeightedCodebookFileHeader.LABEL_GROUPS; pa.codebookHeader.numNeighboursInLabelGroups = 1; //Mapping of average features collected across label groups (i.e. vowels, consonants, etc)
        //pa.codebookHeader.codebookType = WeightedCodebookFileHeader.SPEECH; //Mapping of average features collected across all speech parts (i.e. like spectral equalization)

        pa.codebookHeader.sourceTag = "neutralF"; //Source name tag (i.e. style or speaker identity)
        pa.codebookHeader.targetTag = "happyF"; //Target name tag (i.e. style or speaker identity)
        
        pa.trainingBaseFolder = "D:\\Oytun\\DFKI\\voices\\Interspeech08_out\\neutral2happy"; //Training base directory
        pa.sourceTrainingFolder = "D:\\Oytun\\DFKI\\voices\\Interspeech08\\neutral\\train_200"; //Source training folder
        pa.targetTrainingFolder = "D:\\Oytun\\DFKI\\voices\\Interspeech08\\happy\\train_200"; //Target training folder

        pa.indexMapFileExtension = ".imf"; //Index map file extensions
        
        pa.codebookHeader.lsfParams.lpOrder = 20; //Auto set
        pa.codebookHeader.lsfParams.preCoef = 0.97f;
        pa.codebookHeader.lsfParams.skipsize = 0.010f;
        pa.codebookHeader.lsfParams.winsize = 0.020f;
        pa.codebookHeader.lsfParams.windowType = Window.HAMMING;
        
        String baseFile = StringUtils.checkLastSlash(pa.trainingBaseFolder) + pa.codebookHeader.sourceTag + "_X_" + pa.codebookHeader.targetTag;
        pa.codebookFile = baseFile + "_200" + WeightedCodebookFile.DEFAULT_EXTENSION;
        pa.pitchMappingFile = baseFile + "_200" + PitchMappingFile.DEFAULT_EXTENSION;
        
        pa.isForcedAnalysis = false;
        
        pa.codebookHeader.ptcParams.ws = 0.040;
        pa.codebookHeader.ptcParams.ss = 0.005;
        pa.codebookHeader.ptcParams.voicingThreshold = 0.30;
        pa.codebookHeader.ptcParams.isDoublingCheck = false;
        pa.codebookHeader.ptcParams.isHalvingCheck = false;
        pa.codebookHeader.ptcParams.minimumF0 = 40.0f;
        pa.codebookHeader.ptcParams.maximumF0 = 400.0f;
        pa.codebookHeader.ptcParams.centerClippingRatio = 0.3;
        pa.codebookHeader.ptcParams.cutOff1 = pa.codebookHeader.ptcParams.minimumF0-20.0;
        pa.codebookHeader.ptcParams.cutOff2 = pa.codebookHeader.ptcParams.maximumF0+200.0;
        
        pa.codebookHeader.energyParams.windowSizeInSeconds = 0.020;
        pa.codebookHeader.energyParams.skipSizeInSeconds = 0.010;
        
        TotalStandardDeviations tsd = new TotalStandardDeviations();
        tsd.lsf = 1.5;
        tsd.f0 = 1.0;
        tsd.duration = 1.0;
        tsd.energy = 2.0;
        
        //Gaussian outlier eliminator
        //Decreasing totalStandardDeviations will lead to more outlier eliminations, i.e. smaller codebooks
        pa.gaussianEliminatorParams.isActive = true; //Set to false if you do not want to use this eliminator at all      
        pa.gaussianEliminatorParams.isCheckLsfOutliers = true;
        pa.gaussianEliminatorParams.isEliminateTooSimilarLsf = true;
        pa.gaussianEliminatorParams.isCheckF0Outliers = true; 
        pa.gaussianEliminatorParams.isCheckDurationOutliers = true;    
        pa.gaussianEliminatorParams.isCheckEnergyOutliers = true;
        pa.gaussianEliminatorParams.totalStandardDeviations = new TotalStandardDeviations(tsd);
        //
        
        //KMeans one-to-many and many-to-one mapping eliminator
        pa.kmeansEliminatorParams.isActive = true; //Set to false if you do not want to use this eliminator at all
        
        //pa.kmeansEliminatorParams.eliminationAlgorithm = KMeansMappingEliminatorParams.ELIMINATE_LEAST_LIKELY_MAPPINGS; 
        //pa.kmeansEliminatorParams.eliminationLikelihood = 0.20;
        
        pa.kmeansEliminatorParams.eliminationAlgorithm = KMeansMappingEliminatorParams.ELIMINATE_MEAN_DISTANCE_MISMATCHES; 
        pa.kmeansEliminatorParams.distanceType = DistanceComputer.NORMALIZED_EUCLIDEAN_DISTANCE;
        //pa.kmeansEliminatorParams.distanceType = DistanceComputer.EUCLIDEAN_DISTANCE;
        pa.kmeansEliminatorParams.isGlobalVariance = true;
        
        //pa.kmeansEliminatorParams.eliminationAlgorithm = KMeansMappingEliminatorParams.ELIMINATE_USING_SUBCLUSTER_MEAN_DISTANCES;
        
        pa.kmeansEliminatorParams.isSeparateClustering = false; //Cluster features separately(true) or together(false)?
        
        //Effective only when isSeparateClustering clustering is false
        tsd.general = 0.1;
        pa.kmeansEliminatorParams.numClusters = 30;
        
        //Effective only when isSeparateClustering clustering is true
        tsd.lsf = 1.0;
        tsd.f0 = 1.0;
        tsd.duration = 1.0;
        tsd.energy = 1.0;
        pa.kmeansEliminatorParams.numClustersLsf = 30;
        pa.kmeansEliminatorParams.numClustersF0 = 50;
        pa.kmeansEliminatorParams.numClustersDuration = 5;
        pa.kmeansEliminatorParams.numClustersEnergy = 5;
        
        pa.kmeansEliminatorParams.isCheckLsfOutliers = true;    
        pa.kmeansEliminatorParams.isCheckF0Outliers = false; 
        pa.kmeansEliminatorParams.isCheckDurationOutliers = false;  
        pa.kmeansEliminatorParams.isCheckEnergyOutliers = false;
        //
        
        pa.kmeansEliminatorParams.totalStandardDeviations = new TotalStandardDeviations(tsd);
        //
        
        WeightedCodebookParallelTrainer t = new WeightedCodebookParallelTrainer(pp, fe, pa);
        
        t.run();
        
        System.out.println("Training completed...");
    }
    
    public static void mainSadF() throws UnsupportedAudioFileException, IOException
    {
        BaselinePreprocessor pp = new BaselinePreprocessor();
        BaselineFeatureExtractor fe = new BaselineFeatureExtractor();
        
        WeightedCodebookTrainerParams pa = new WeightedCodebookTrainerParams();
        
        pa.codebookHeader.codebookType = WeightedCodebookFileHeader.FRAMES; //Frame-by-frame mapping of features
        //pa.codebookHeader.codebookType = WeightedCodebookFileHeader.FRAME_GROUPS; pa.codebookHeader.numNeighboursInFrameGroups = 3; //Mapping of frame average features (no label information but fixed amount of neighbouring frames is used)
        //pa.codebookHeader.codebookType = WeightedCodebookFileHeader.LABELS; //Mapping of label average features
        //pa.codebookHeader.codebookType = WeightedCodebookFileHeader.LABEL_GROUPS; pa.codebookHeader.numNeighboursInLabelGroups = 1; //Mapping of average features collected across label groups (i.e. vowels, consonants, etc)
        //pa.codebookHeader.codebookType = WeightedCodebookFileHeader.SPEECH; //Mapping of average features collected across all speech parts (i.e. like spectral equalization)

        pa.codebookHeader.sourceTag = "neutralF"; //Source name tag (i.e. style or speaker identity)
        pa.codebookHeader.targetTag = "sadF"; //Target name tag (i.e. style or speaker identity)
        
        pa.trainingBaseFolder = "D:\\Oytun\\DFKI\\voices\\Interspeech08_out\\neutral2sad"; //Training base directory
        pa.sourceTrainingFolder = "D:\\Oytun\\DFKI\\voices\\Interspeech08\\neutral\\train_200"; //Source training folder
        pa.targetTrainingFolder = "D:\\Oytun\\DFKI\\voices\\Interspeech08\\sad\\train_200"; //Target training folder

        pa.indexMapFileExtension = ".imf"; //Index map file extensions
        
        pa.codebookHeader.lsfParams.lpOrder = 20; //Auto set
        pa.codebookHeader.lsfParams.preCoef = 0.97f;
        pa.codebookHeader.lsfParams.skipsize = 0.010f;
        pa.codebookHeader.lsfParams.winsize = 0.020f;
        pa.codebookHeader.lsfParams.windowType = Window.HAMMING;
        
        String baseFile = StringUtils.checkLastSlash(pa.trainingBaseFolder) + pa.codebookHeader.sourceTag + "_X_" + pa.codebookHeader.targetTag;
        pa.codebookFile = baseFile + "_200" + WeightedCodebookFile.DEFAULT_EXTENSION;
        pa.pitchMappingFile = baseFile + "_200" + PitchMappingFile.DEFAULT_EXTENSION;
        
        pa.isForcedAnalysis = false;
        
        pa.codebookHeader.ptcParams.ws = 0.040;
        pa.codebookHeader.ptcParams.ss = 0.005;
        pa.codebookHeader.ptcParams.voicingThreshold = 0.30;
        pa.codebookHeader.ptcParams.isDoublingCheck = false;
        pa.codebookHeader.ptcParams.isHalvingCheck = false;
        pa.codebookHeader.ptcParams.minimumF0 = 40.0f;
        pa.codebookHeader.ptcParams.maximumF0 = 400.0f;
        pa.codebookHeader.ptcParams.centerClippingRatio = 0.3;
        pa.codebookHeader.ptcParams.cutOff1 = pa.codebookHeader.ptcParams.minimumF0-20.0;
        pa.codebookHeader.ptcParams.cutOff2 = pa.codebookHeader.ptcParams.maximumF0+200.0;
        
        pa.codebookHeader.energyParams.windowSizeInSeconds = 0.020;
        pa.codebookHeader.energyParams.skipSizeInSeconds = 0.010;
        
        TotalStandardDeviations tsd = new TotalStandardDeviations();
        tsd.lsf = 1.5;
        tsd.f0 = 1.0;
        tsd.duration = 1.0;
        tsd.energy = 2.0;
        
        //Gaussian outlier eliminator
        //Decreasing totalStandardDeviations will lead to more outlier eliminations, i.e. smaller codebooks
        pa.gaussianEliminatorParams.isActive = true; //Set to false if you do not want to use this eliminator at all      
        pa.gaussianEliminatorParams.isCheckLsfOutliers = true;
        pa.gaussianEliminatorParams.isEliminateTooSimilarLsf = true;
        pa.gaussianEliminatorParams.isCheckF0Outliers = true; 
        pa.gaussianEliminatorParams.isCheckDurationOutliers = true;    
        pa.gaussianEliminatorParams.isCheckEnergyOutliers = true;
        pa.gaussianEliminatorParams.totalStandardDeviations = new TotalStandardDeviations(tsd);
        //
        
        //KMeans one-to-many and many-to-one mapping eliminator
        pa.kmeansEliminatorParams.isActive = true; //Set to false if you do not want to use this eliminator at all
        
        //pa.kmeansEliminatorParams.eliminationAlgorithm = KMeansMappingEliminatorParams.ELIMINATE_LEAST_LIKELY_MAPPINGS; 
        //pa.kmeansEliminatorParams.eliminationLikelihood = 0.20;
        
        pa.kmeansEliminatorParams.eliminationAlgorithm = KMeansMappingEliminatorParams.ELIMINATE_MEAN_DISTANCE_MISMATCHES; 
        pa.kmeansEliminatorParams.distanceType = DistanceComputer.NORMALIZED_EUCLIDEAN_DISTANCE;
        //pa.kmeansEliminatorParams.distanceType = DistanceComputer.EUCLIDEAN_DISTANCE;
        pa.kmeansEliminatorParams.isGlobalVariance = true;
        
        //pa.kmeansEliminatorParams.eliminationAlgorithm = KMeansMappingEliminatorParams.ELIMINATE_USING_SUBCLUSTER_MEAN_DISTANCES;
        
        pa.kmeansEliminatorParams.isSeparateClustering = false; //Cluster features separately(true) or together(false)?
        
        //Effective only when isSeparateClustering clustering is false
        tsd.general = 0.1;
        pa.kmeansEliminatorParams.numClusters = 30;
        
        //Effective only when isSeparateClustering clustering is true
        tsd.lsf = 1.0;
        tsd.f0 = 1.0;
        tsd.duration = 1.0;
        tsd.energy = 1.0;
        pa.kmeansEliminatorParams.numClustersLsf = 30;
        pa.kmeansEliminatorParams.numClustersF0 = 50;
        pa.kmeansEliminatorParams.numClustersDuration = 5;
        pa.kmeansEliminatorParams.numClustersEnergy = 5;
        
        pa.kmeansEliminatorParams.isCheckLsfOutliers = true;    
        pa.kmeansEliminatorParams.isCheckF0Outliers = false; 
        pa.kmeansEliminatorParams.isCheckDurationOutliers = false;  
        pa.kmeansEliminatorParams.isCheckEnergyOutliers = false;
        //
        
        pa.kmeansEliminatorParams.totalStandardDeviations = new TotalStandardDeviations(tsd);
        //
        
        WeightedCodebookParallelTrainer t = new WeightedCodebookParallelTrainer(pp, fe, pa);
        
        t.run();
        
        System.out.println("Training completed...");
    }
}