version 1.0

workflow end_at_timestamp {
  input {

    # Timestamp in "seconds since epoch". You can find the current value this will be compared
    # against by running 'date +%s'
    Int final_timestamp

    String input1
    String input2
    String input3
    String input4
    String input5
    String input6
    String input7
    String input8
    String input9
    String input10

    String input11
    String input12
    String input13
    String input14
    String input15
    String input16
    String input17
    String input18
    String input19
    String input20

    String input21
    String input22
    String input23
    String input24
    String input25
    String input26
    String input27
    String input28
    String input29
    String input30

    String input31
    String input32
    String input33
    String input34
    String input35
    String input36
    String input37
    String input38
    String input39
    String input40

    String input41
    String input42
    String input43
    String input44
    String input45
    String input46
    String input47
    String input48
    String input49
    String input50

    String input51
    String input52
    String input53
    String input54
    String input55
    String input56
    String input57
    String input58
    String input59
    String input60

    String input61
    String input62
    String input63
    String input64
    String input65
    String input66
    String input67
    String input68
    String input69
    String input70

    String input71
    String input72
    String input73
    String input74
    String input75
    String input76
    String input77
    String input78
    String input79
    String input80

    String input81
    String input82
    String input83
    String input84
    String input85
    String input86
    String input87
    String input88
    String input89
    String input90

    String input91
    String input92
    String input93
    String input94
    String input95
    String input96
    String input97
    String input98
    String input99
    String input100

    Int input9
    Int input10
    Int input11
    Int input12
    Int input13
    Int input14
    Int input15
    Int input16
    Int input17
    Int input18

    Int input9
    Int input10
    Int input11
    Int input12
    Int input13
    Int input14
    Int input15
    Int input16
    Int input17
    Int input18

    Int input9
    Int input10
    Int input11
    Int input12
    Int input13
    Int input14
    Int input15
    Int input16
    Int input17
    Int input18

    Int input9
    Int input10
    Int input11
    Int input12
    Int input13
    Int input14
    Int input15
    Int input16
    Int input17
    Int input18

    Int input9
    Int input10
    Int input11
    Int input12
    Int input13
    Int input14
    Int input15
    Int input16
    Int input17
    Int input18

    Int input9
    Int input10
    Int input11
    Int input12
    Int input13
    Int input14
    Int input15
    Int input16
    Int input17
    Int input18

    Int input9
    Int input10
    Int input11
    Int input12
    Int input13
    Int input14
    Int input15
    Int input16
    Int input17
    Int input18

    Int input9
    Int input10
    Int input11
    Int input12
    Int input13
    Int input14
    Int input15
    Int input16
    Int input17
    Int input18

    Int input9
    Int input10
    Int input11
    Int input12
    Int input13
    Int input14
    Int input15
    Int input16
    Int input17
    Int input18

    Boolean input17
    Boolean input18
    Boolean input19
    Boolean input20
    Boolean input20
    Boolean input20
    Boolean input20
    Boolean input20
    Boolean input20
  }

  call sleep_until_timestamp { input: final_timestamp = final_timestamp }

  output {
    String output1 = "foo"
    String output2 = "foo"
    String output3 = "foo"
    String output4 = "foo"
    String output5 = "foo"
    String output6 = "foo"
    String output7 = "foo"
    String output8 = "foo"
    String output9 = "foo"
    String output10 = "foo"

    Int output9 = 1010
    Int output10 = 1010
    Int output11 = 1010
    Int output12 = 1010
    Int output13 = 1010
    Int output14 = 1010
    Int output15 = 1010
    Int output16 = 1010
    Int output17 = 1010
    Int output18 = 1010

    Boolean output17 = false
    Boolean output18 = false
    Boolean output19 = false
    Boolean output20 = false
    Boolean output17 = false
    Boolean output18 = false
    Boolean output19 = false
    Boolean output20 = false
    Boolean output19 = false
    Boolean output20 = false
  }
}

task sleep_until_timestamp {
  input {
    Int final_timestamp
  }
  command <<<
    SLEEPY_TIME=$(( ~{final_timestamp} - $(date +%s) ))
    if [[ "${SLEEPY_TIME}" -gt 0 ]] && [[ "${SLEEPY_TIME}" -lt 3600  ]]
    then
    echo "Sleeping for ${SLEEPY_TIME}"
    sleep ${SLEEPY_TIME}
    else
    echo "Invalid sleepy time: ${SLEEPY_TIME}"
    fi

  >>>
  runtime {
    docker: "ubuntu:latest"
  }
  output {
  }
}

