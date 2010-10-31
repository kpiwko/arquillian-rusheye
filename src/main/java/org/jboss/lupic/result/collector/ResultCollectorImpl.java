/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.lupic.result.collector;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.lupic.core.ComparisonResult;
import org.jboss.lupic.result.ResultConclusion;
import org.jboss.lupic.result.ResultDetail;
import org.jboss.lupic.result.ResultEvaluator;
import org.jboss.lupic.result.statistics.ResultStatistics;
import org.jboss.lupic.result.storage.ResultStorage;
import org.jboss.lupic.result.writer.ResultWriter;
import org.jboss.lupic.suite.Pattern;
import org.jboss.lupic.suite.Test;
import org.jboss.lupic.suite.VisualSuite;
import org.jboss.lupic.suite.utils.Instantiator;

/**
 * @author <a href="mailto:lfryc@redhat.com">Lukas Fryc</a>
 * @version $Revision$
 */
public class ResultCollectorImpl extends ResultCollectorAdapter {

    Properties properties;
    ResultStorage storage;
    ResultEvaluator evaluator;
    ResultWriter writer;
    ResultStatistics statistics;

    ConcurrentMap<Test, List<ResultDetail>> details = new ConcurrentHashMap<Test, List<ResultDetail>>();

    @Override
    public void setProperties(Properties properties) {
        super.setProperties(properties);
    }

    @Override
    public void onConfigurationParsed(VisualSuite visualSuite) {
        String storageClass = (String) properties.get("result-storage");
        storage = new Instantiator<ResultStorage>().getInstance(storageClass);
        storage.setProperties(properties);
        
        String writerClass = (String) properties.get("result-writer");
        writer = new Instantiator<ResultWriter>().getInstance(writerClass);
        writer.setProperties(properties);
        
        String statisticsClass = (String) properties.get("result-statistics");
        statistics = new Instantiator<ResultStatistics>().getInstance(statisticsClass);
        statistics.setProperties(properties);

        evaluator = new ResultEvaluator();
    }

    @Override
    public void onPatternCompleted(Test test, Pattern pattern, ComparisonResult comparisonResult) {
        ResultDetail detail = new ResultDetail();

        ResultConclusion conclusion = evaluator.evaluate(test.getPerception(), comparisonResult);
        detail.setConclusion(conclusion);

        if (conclusion == ResultConclusion.DIFFER || conclusion == ResultConclusion.PERCEPTUALLY_SAME) {
            String location = storage.write(test, pattern, comparisonResult.getDiffImage());
            detail.setLocation(location);
        }

        if (comparisonResult.getDiffImage() != null) {
            comparisonResult.getDiffImage().flush();
        }

        detail.setPattern(pattern);

        if (!details.containsKey(test)) {
            details.putIfAbsent(test, new CopyOnWriteArrayList<ResultDetail>());
        }
        details.get(test).add(detail);
        
        statistics.onPatternCompleted(detail);
    }

    @Override
    public void onTestCompleted(Test test) {
        List<ResultDetail> detailList = details.get(test);
        details.remove(test);

        writer.write(test, detailList);
        statistics.onTestCompleted(test, detailList);
    }

    @Override
    public void onSuiteCompleted(VisualSuite visualSuite) {
        writer.close();
        statistics.onSuiteCompleted();
    }
}