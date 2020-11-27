#!/bin/bash

if [[ $# != 1 ]]
	then echo "Usage : lauch_test test_name"; exit
fi

###### PARAMETERS
nb_files=2
files_mbytes=1024
hole=9       #pour les fixed_holes on écrit nb_writes pages et on en saute hole
nb_writes=1
pourcentage=100 #pourcentage de page écrite pour le mode random

###### DIRECTORIES
res_dir="results"

###### OUTPUTS
name="$1_test1"
logs_name="$res_dir/$name"
out_name="$res_dir/$name.stdout"
graphs_name="$res_dir/${name}.pdf"



##### TMP FILES
graphs1_name="$res_dir/${name}1.pdf"
graphs2_name="$res_dir/${name}2.pdf"
blkparse_name="$res_dir/$name.blkparse"
plot_name="$res_dir/$name-out"

###### BENCH
bench_exec="./bench -m $1 -f $nb_files -s $files_mbytes -o $logs_name -h $hole -w $nb_writes -r $pourcentage"


#création du répertoire contenant les resultats
mkdir -p $res_dir

# créer les fichiers pour le bench
echo "Création des fichiers pour le bench"
for i in `seq 1 $nb_files`
do
	dd if=/dev/zero of=file.$i bs=1M count=$files_mbytes
done

# sync
echo "Sync..."
sync

# on coupe le ratio et background ratio
sysctl vm.dirty_ratio=100
sysctl vm.dirty_background_ratio=100
sysctl vm.dirty_writeback_centisecs=0
sysctl vm.dirty_expire_centisecs=10000

#on désactive le swap
swapoff -a

# clear du cache
echo "Vidage du cache"
echo 3 > /proc/sys/vm/drop_caches

# politique I/Oscheduler = noop
#echo "Politique scheduler mise à noop"
#echo noop > /sys/block/sda/queue/scheduler


# lancer le bench
echo "Running.."
$bench_exec

# politique I/Oscheduler = cfq (celle par default sur linux)
#echo "Politique scheduler mise à cfq"
#echo cfq > /sys/block/sda/queue/scheduler

sysctl vm.dirty_ratio=20
sysctl vm.dirty_background_ratio=10
sysctl vm.dirty_writeback_centisecs=0
sysctl vm.dirty_expire_centisecs=3000
swapon -a

# suppression des fichiers pour les benchs
echo "Suppression des fichiers pour le bench"
for i in `seq 1 $nb_files`
do
	rm file.$i
done

# création des courbes
echo "Génération des courbes"
seekwatcher -t $logs_name -o $graphs1_name > /dev/null
blkparse $logs_name -o $blkparse_name
cd Parser/bin
java fr/upmc/stage/parser/Main -i ../../$blkparse_name -o ../../$plot_name > ../../$out_name
cd ../..
gnuplot  << EOF
  set terminal pdf
  set output "$graphs2_name"
  set xr [*:*]
  set yr [*:*]
  set xlabel "Time Queued (s)"
  set ylabel "Delay (s)"
  set style line 1 lt 1 lc 1 lw 1
  set style line 2 lt 1 lc 3 lw 1
  plot "$plot_name/avGraphQC.txt" using 1:2 ls 1 title 'Time Queued -> Time Completed' with lines,\
  "$plot_name/avGraphQD.txt" using 1:2 ls 2 title 'Time Queued -> Time Submitted' with lines
  set xlabel "Time Queued (s)"
  set ylabel "Delay (s)"
  plot "$plot_name/avGraphDC.txt" using 1:2 ls 1 title 'Time Submitted -> Time Completed' with lines
  set xlabel "Time Queued (s)"
  set ylabel "Number of sector"
  plot "$plot_name/graphIOSize.txt" using 1:2 ls 1 title 'IO Size' with circle
  set xlabel "Time (s)"
  set ylabel "Number of sector"
  plot "$plot_name/graphIOQueueSize.txt" using 1:2 ls 1 title 'IO Queue Size' with lines
  set xlabel "Time completed (s)"
  set ylabel "Block number"
  plot "$plot_name/graphBlkNums.txt" using 1:2 ls 2 title 'Blocks written' with dots
EOF

rm -rf $plot_name $blkparse_name $graphs_name
pdftk $graphs1_name $graphs2_name cat output $graphs_name
rm -rf $plot_name $blkparse_name $graphs1_name $graphs2_name 

