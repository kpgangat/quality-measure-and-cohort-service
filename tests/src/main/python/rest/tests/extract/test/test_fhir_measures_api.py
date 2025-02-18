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
from swagger_client.api.fhir_measures_api import FHIRMeasuresApi  # noqa: E501
from swagger_client.rest import ApiException


class TestFHIRMeasuresApi(unittest.TestCase):
    """FHIRMeasuresApi unit test stubs"""

    def setUp(self):
        self.api = swagger_client.api.fhir_measures_api.FHIRMeasuresApi()  # noqa: E501

    def tearDown(self):
        pass

    def test_get_measure_parameters(self):
        """Test case for get_measure_parameters

        Get measure parameters  # noqa: E501
        """
        pass

    def test_get_measure_parameters_by_id(self):
        """Test case for get_measure_parameters_by_id

        Get measure parameters by id  # noqa: E501
        """
        pass


if __name__ == '__main__':
    unittest.main()
