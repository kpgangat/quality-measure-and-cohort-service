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
from swagger_client.api.value_set_api import ValueSetApi  # noqa: E501
from swagger_client.rest import ApiException


class TestValueSetApi(unittest.TestCase):
    """ValueSetApi unit test stubs"""

    def setUp(self):
        self.api = swagger_client.api.value_set_api.ValueSetApi()  # noqa: E501

    def tearDown(self):
        pass

    def test_create_value_set(self):
        """Test case for create_value_set

        Insert a new value set to the fhir server or, if it already exists, update it in place  # noqa: E501
        """
        pass


if __name__ == '__main__':
    unittest.main()
