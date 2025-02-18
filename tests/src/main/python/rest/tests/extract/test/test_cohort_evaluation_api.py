# coding: utf-8

"""
    IBM Cohort Engine

    Service to evaluate cohorts and measures  # noqa: E501

    OpenAPI spec version: 0.0.1 2021-07-22T12:47:28Z
    
    Generated by: https://github.com/swagger-api/swagger-codegen.git
"""


from __future__ import absolute_import

import unittest

import swagger_client
from swagger_client.api.cohort_evaluation_api import CohortEvaluationApi  # noqa: E501
from swagger_client.rest import ApiException


class TestCohortEvaluationApi(unittest.TestCase):
    """CohortEvaluationApi unit test stubs"""

    def setUp(self):
        self.api = swagger_client.api.cohort_evaluation_api.CohortEvaluationApi()  # noqa: E501

    def tearDown(self):
        pass

    def test_evaluate_cohort(self):
        """Test case for evaluate_cohort

        Evaluates a specific define within a CQL for a set of patients  # noqa: E501
        """
        pass


if __name__ == '__main__':
    unittest.main()
